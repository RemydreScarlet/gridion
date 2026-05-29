package gridion.gpu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import gridion.gpu.simt.{WarpScheduler, Opcode}

class WarpSchedulerTestWrapper(numWarps: Int = 2, memSize: Int = 64) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val kernelAddr = Input(UInt(12.W))
    val writeEn = Input(Bool())
    val writeAddr = Input(UInt(12.W))
    val writeData = Input(UInt(16.W))
    val memDone = Input(Bool())
    val schedDone = Output(Bool())
    val laneOpcode = Output(UInt(6.W))
    val laneDst = Output(UInt(4.W))
    val laneSrc1 = Output(UInt(4.W))
    val laneSrc2 = Output(UInt(4.W))
    val laneIssue = Output(Bool())
    val laneCommit = Output(Bool())
    val barrierAllReached = Output(Bool())
    val instAddr = Output(UInt(12.W))
  })

  val instrMem = RegInit(VecInit(Seq.fill(memSize)(0.U(16.W))))
  val sched = Module(new WarpScheduler(numWarps))

  when(io.writeEn) {
    instrMem(io.writeAddr) := io.writeData
  }

  sched.io.start := io.start
  sched.io.kernelAddr := io.kernelAddr
  sched.io.instData := instrMem(sched.io.instAddr)
  sched.io.memDone := io.memDone

  io.schedDone := sched.io.done
  io.laneOpcode := sched.io.laneOpcode
  io.laneDst := sched.io.laneDst
  io.laneSrc1 := sched.io.laneSrc1
  io.laneSrc2 := sched.io.laneSrc2
  io.laneIssue := sched.io.laneIssue
  io.laneCommit := sched.io.laneCommit
  io.barrierAllReached := sched.io.barrierAllReached
  io.instAddr := sched.io.instAddr
}

class WarpSchedulerTest extends AnyFlatSpec with ChiselScalatestTester {
  def makeRR(opcode: Int, dst: Int, src1: Int, src2: Int): Int = {
    (opcode << 10) | (dst << 7) | (src1 << 4) | src2
  }

  def makeBranch(opcode: Int, imm: Int, pred: Int): Int = {
    (opcode << 10) | (imm << 4) | pred
  }

  behavior of "WarpScheduler"

  it should "start and execute NOP" in {
    test(new WarpSchedulerTestWrapper(numWarps = 2)) { c =>
      c.io.writeEn.poke(true.B)
      c.io.writeAddr.poke(0.U)
      c.io.writeData.poke(0.U) // NOP
      c.clock.step(1)
      c.io.writeEn.poke(false.B)

      c.io.start.poke(true.B)
      c.io.kernelAddr.poke(0.U)
      c.io.memDone.poke(false.B)
      c.clock.step(1) // start fires

      c.io.start.poke(false.B)
      c.clock.step(1) // first instruction fetched and processed

      assert(c.io.laneIssue.peekBoolean(), "laneIssue should be true")
      assert(c.io.laneCommit.peekBoolean(), "laneCommit should be true")
      assert(c.io.laneOpcode.peekInt() == 0, "opcode should be NOP")
    }
  }

  it should "execute IADD instruction from microcode" in {
    test(new WarpSchedulerTestWrapper(numWarps = 2)) { c =>
      val iadd = makeRR(25, 1, 0, 0) // IADD R1, R0, R0
      c.io.writeEn.poke(true.B)
      c.io.writeAddr.poke(0.U)
      c.io.writeData.poke(iadd.U)
      c.clock.step(1)
      c.io.writeEn.poke(false.B)

      c.io.start.poke(true.B)
      c.io.kernelAddr.poke(0.U)
      c.clock.step(1)
      c.io.start.poke(false.B)
      c.clock.step(1)

      assert(c.io.laneIssue.peekBoolean(), "laneIssue should be true")
      assert(c.io.laneCommit.peekBoolean(), "laneCommit should be true")
      assert(c.io.laneOpcode.peekInt() == 25, s"opcode should be IADD (25), got ${c.io.laneOpcode.peekInt()}")
      assert(c.io.laneDst.peekInt() == 1, "dst should be 1")
      assert(c.io.laneSrc1.peekInt() == 0, "src1 should be 0")
      assert(c.io.laneSrc2.peekInt() == 0, "src2 should be 0")
    }
  }

  it should "advance PC through multiple instructions" in {
    test(new WarpSchedulerTestWrapper(numWarps = 2)) { c =>
      val nop = 0
      val iadd = makeRR(25, 1, 0, 0)

      c.io.writeEn.poke(true.B)
      c.io.writeAddr.poke(0.U); c.io.writeData.poke(nop.U); c.clock.step(1)
      c.io.writeAddr.poke(1.U); c.io.writeData.poke(iadd.U); c.clock.step(1)
      c.io.writeEn.poke(false.B)

      c.io.start.poke(true.B); c.io.kernelAddr.poke(0.U); c.clock.step(1)
      c.io.start.poke(false.B)

      c.clock.step(1) // NOP issued
      assert(c.io.laneOpcode.peekInt() == 0, "first: NOP")

      c.clock.step(1) // IADD issued
      assert(c.io.laneOpcode.peekInt() == 25, s"second: IADD (25), got ${c.io.laneOpcode.peekInt()}")
    }
  }

  it should "execute BR branch forward" in {
    test(new WarpSchedulerTestWrapper(numWarps = 2)) { c =>
      val nop = 0
      val iadd1 = makeRR(25, 1, 0, 0)
      val iadd2 = makeRR(25, 2, 0, 0)
      val br = makeBranch(40, 2, 0) // BR +2: skip IADD1, go to IADD2

      c.io.writeEn.poke(true.B)
      c.io.writeAddr.poke(0.U); c.io.writeData.poke(br.U); c.clock.step(1)
      c.io.writeAddr.poke(1.U); c.io.writeData.poke(iadd1.U); c.clock.step(1)
      c.io.writeAddr.poke(2.U); c.io.writeData.poke(iadd2.U); c.clock.step(1)
      c.io.writeAddr.poke(3.U); c.io.writeData.poke(nop.U); c.clock.step(1)
      c.io.writeEn.poke(false.B)

      c.io.start.poke(true.B); c.io.kernelAddr.poke(0.U); c.clock.step(1)
      c.io.start.poke(false.B)

      c.clock.step(1) // BR: pc = 0 + 2 = 2
      assert(c.io.laneOpcode.peekInt() == 40, "first: BR")

      c.clock.step(1) // IADD2 at addr 2
      assert(c.io.laneOpcode.peekInt() == 25, s"second: IADD (25), got ${c.io.laneOpcode.peekInt()}")
      assert(c.io.laneDst.peekInt() == 2, "dst should be 2")
    }
  }

  it should "process BARRIER" in {
    test(new WarpSchedulerTestWrapper(numWarps = 2)) { c =>
      val nop = 0
      val barrier = makeRR(45, 0, 0, 0) // BARRIER
      val iadd = makeRR(25, 1, 0, 0)

      c.io.writeEn.poke(true.B)
      c.io.writeAddr.poke(0.U); c.io.writeData.poke(barrier.U); c.clock.step(1)
      c.io.writeAddr.poke(1.U); c.io.writeData.poke(iadd.U); c.clock.step(1)
      c.io.writeEn.poke(false.B)

      c.io.start.poke(true.B); c.io.kernelAddr.poke(0.U); c.clock.step(1)
      c.io.start.poke(false.B)

      c.clock.step(2) // BARRIER processed (latency 4)
      // After BARRIER, warp is waiting, so no issue
      val issueAfterBarrier = c.io.laneIssue.peekBoolean()

      // Check barrier reached (both warps waiting, only 2 warps)
      c.clock.step(5)
    }
  }

  it should "track memory operations" in {
    test(new WarpSchedulerTestWrapper(numWarps = 2)) { c =>
      val gload = makeRR(33, 1, 0, 0) // GLOAD R1, R0, R0

      c.io.writeEn.poke(true.B)
      c.io.writeAddr.poke(0.U); c.io.writeData.poke(gload.U); c.clock.step(1)
      c.io.writeAddr.poke(1.U); c.io.writeData.poke(0.U); c.clock.step(1)
      c.io.writeEn.poke(false.B)

      c.io.start.poke(true.B); c.io.kernelAddr.poke(0.U); c.clock.step(1)
      c.io.start.poke(false.B)

      c.clock.step(1) // GLOAD issued, pendingMemOps = 1
      assert(c.io.laneOpcode.peekInt() == 33, "should be GLOAD")

      // After memDone, scheduler should continue
      c.io.memDone.poke(true.B)
      c.clock.step(1)
      c.io.memDone.poke(false.B)

      c.clock.step(1) // should advance to next instruction
      assert(c.io.laneOpcode.peekInt() == 0, "should be NOP after mem")
    }
  }
}
