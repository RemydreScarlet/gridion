package gridion.gpu.simt

import chisel3._
import chisel3.util._

class WarpState extends Bundle {
  val pc = UInt(12.W)
  val activeMask = UInt(64.W)
  val pendingMemOps = UInt(4.W)
  val barrierWaiting = Bool()
  val killed = Bool()
}

class WarpScheduler(val numWarps: Int = 4) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val kernelAddr = Input(UInt(12.W))

    val instAddr = Output(UInt(12.W))
    val instData = Input(UInt(16.W))

    val laneOpcode = Output(UInt(6.W))
    val laneDst = Output(UInt(4.W))
    val laneSrc1 = Output(UInt(4.W))
    val laneSrc2 = Output(UInt(4.W))
    val laneDx = Output(UInt(2.W))
    val laneDy = Output(UInt(2.W))
    val laneIssue = Output(Bool())
    val laneCommit = Output(Bool())

    val memDone = Input(Bool())

    val barrierAllReached = Output(Bool())
    val done = Output(Bool())
  })

  val warpStates = Reg(Vec(numWarps, new WarpState))

  val currWarp = RegInit(0.U(log2Ceil(numWarps).W))
  val stallCount = RegInit(0.U(4.W))
  val pipeStage = RegInit(0.U(2.W))
  val issuing = RegInit(false.B)
  val running = RegInit(false.B)

  val currState = warpStates(currWarp)

  val inst = io.instData
  val opcode = inst(15, 10)
  val dst = inst(9, 7)
  val src1 = inst(6, 4)
  val src2 = inst(3, 0)
  val dx = inst(3, 2)
  val dy = inst(1, 0)
  val imm = inst(9, 4)
  val predReg = inst(3, 0)

  val lat = Opcode.latency(opcode)
  val isBranch = Opcode.isBrOp(opcode)
  val isMem = Opcode.isMemOp(opcode)
  val isBarrier = opcode === Opcode.BARRIER

  val brImm = imm
  val brPred = predReg

  io.instAddr := currState.pc

  val warpReady = Wire(Vec(numWarps, Bool()))
  for (w <- 0 until numWarps) {
    warpReady(w) := !warpStates(w).barrierWaiting && !warpStates(w).killed &&
                     warpStates(w).pendingMemOps === 0.U
  }

  val barrierCount = RegInit(0.U(log2Ceil(numWarps + 1).W))
  val anyBarrier = warpStates.map(_.barrierWaiting).reduce(_ || _)

  io.barrierAllReached := barrierCount >= numWarps.U

  val nextWarp = Wire(UInt(log2Ceil(numWarps).W))
  val found = Wire(Bool())
  found := false.B
  nextWarp := currWarp

  for (i <- 0 until numWarps) {
    val w = (currWarp + i.U + 1.U) % numWarps.U
    when(!found && warpReady(w)) {
      found := true.B
      nextWarp := w
    }
  }

  val opcodeReg = Reg(UInt(6.W))
  val dstReg = Reg(UInt(4.W))
  val latReg = Reg(UInt(4.W))
  val issueReg = Reg(Bool())
  val commitReg = Reg(Bool())

  when(running) {
    when(stallCount > 0.U) {
      stallCount := stallCount - 1.U
      when(stallCount === 1.U) {
        commitReg := true.B
      }.otherwise {
        commitReg := false.B
      }
      issueReg := false.B
    }.otherwise {
      commitReg := false.B

      when(warpReady(currWarp) && !currState.killed) {
        when(isBranch) {
          val takeBr = Mux(opcode === Opcode.BR, true.B,
                       Mux(opcode === Opcode.BRZ, brPred === 0.U,
                       Mux(opcode === Opcode.BRNZ, brPred =/= 0.U,
                       Mux(opcode === Opcode.CALL, true.B, false.B))))

          when(takeBr) {
            when(opcode === Opcode.CALL) {
              currState.pc := currState.pc + brImm
            }.otherwise {
              currState.pc := currState.pc + brImm
            }
          }.otherwise {
            currState.pc := currState.pc + 1.U
          }

          opcodeReg := opcode
          dstReg := dst
          latReg := 1.U
          issueReg := true.B
          commitReg := true.B
          stallCount := 0.U
        }.elsewhen(isBarrier) {
          currState.barrierWaiting := true.B
          issueReg := false.B
          commitReg := false.B
        }.otherwise {
          opcodeReg := opcode
          dstReg := dst
          latReg := lat
          issueReg := true.B
          when(lat <= 1.U) {
            commitReg := true.B
            stallCount := 0.U
          }.otherwise {
            commitReg := false.B
            stallCount := lat - 1.U
          }
          currState.pc := currState.pc + 1.U
        }
      }.otherwise {
        issueReg := false.B
        commitReg := false.B
      }
    }
  }

  when(io.barrierAllReached) {
    for (w <- 0 until numWarps) {
      warpStates(w).barrierWaiting := false.B
    }
    barrierCount := 0.U
  }

  when(anyBarrier) {
    barrierCount := PopCount(warpStates.map(_.barrierWaiting))
  }

  when(isMem && issueReg) {
    currState.pendingMemOps := currState.pendingMemOps + 1.U
  }

  when(io.memDone) {
    warpStates(currWarp).pendingMemOps := 0.U
  }

  io.laneOpcode := Mux(issueReg || commitReg, opcodeReg, Opcode.NOP)
  io.laneDst := Mux(issueReg, dstReg, 0.U)
  io.laneSrc1 := inst(6, 4)
  io.laneSrc2 := inst(3, 0)
  io.laneDx := inst(3, 2)
  io.laneDy := inst(1, 0)
  io.laneIssue := issueReg
  io.laneCommit := commitReg

  val allKilled = warpStates.map(_.killed).reduce(_ && _)

  when(io.start && !running) {
    for (w <- 0 until numWarps) {
      warpStates(w).pc := io.kernelAddr
      warpStates(w).activeMask := Fill(64, 1.U(1.W)).asUInt
      warpStates(w).pendingMemOps := 0.U
      warpStates(w).barrierWaiting := false.B
      warpStates(w).killed := false.B
    }
    running := true.B
  }

  io.done := running && allKilled
}
