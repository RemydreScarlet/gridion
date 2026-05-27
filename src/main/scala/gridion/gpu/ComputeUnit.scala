package gridion.gpu

import chisel3._
import chisel3.util._
import gridion.posit._
import gridion.gpu.simt._
import gridion.gpu.memory._

class ComputeUnit(val p: PositParams = PositParams(), val numWarps: Int = 4, val microcodeSize: Int = 4096) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val kernelAddr = Input(UInt(12.W))
    val microcodeData = Input(UInt(16.W))
    val microcodeWrEn = Input(Bool())
    val microcodeWrAddr = Input(UInt(12.W))

    val done = Output(Bool())

    val memAddr = Output(UInt(32.W))
    val memWrData = Output(UInt(64.W))
    val memReq = Output(Bool())
    val memWr = Output(Bool())
    val memRespData = Input(UInt(64.W))
    val memRespValid = Input(Bool())
  })

  val microcodeMem = SyncReadMem(microcodeSize, UInt(16.W))

  when(io.microcodeWrEn) {
    microcodeMem.write(io.microcodeWrAddr, io.microcodeData)
  }

  val scheduler = Module(new WarpScheduler(numWarps))
  scheduler.io.start := io.start
  scheduler.io.kernelAddr := io.kernelAddr
  scheduler.io.instData := microcodeMem.read(scheduler.io.instAddr)

  io.done := scheduler.io.done

  val laneMods = Seq.fill(64)(Module(new SIMTLane(p)))
  val laneIOs = VecInit(laneMods.map(_.io))

  laneMods.zipWithIndex.foreach { case (l, idx) =>
    val x = idx % 8
    val y = idx / 8
    l.io.opcode := scheduler.io.laneOpcode
    l.io.dst := scheduler.io.laneDst
    l.io.src1 := scheduler.io.laneSrc1
    l.io.src2 := scheduler.io.laneSrc2
    l.io.dx := scheduler.io.laneDx
    l.io.dy := scheduler.io.laneDy
    l.io.x := x.U
    l.io.y := y.U
    l.io.issue := scheduler.io.laneIssue
    l.io.commit := scheduler.io.laneCommit
  }

  val neighborOffsets = Seq(
    (1, -1), (0, -1), (-1, -1),
    (1,  0),          (-1,  0),
    (1,  1), (0,  1), (-1,  1)
  )

  laneMods.zipWithIndex.foreach { case (l, idx) =>
    val x = idx % 8
    val y = idx / 8
    for (n <- 0 until 8) {
      val (dx, dy) = neighborOffsets(n)
      val nx = (x + dx + 8) % 8
      val ny = (y + dy + 8) % 8
      val nIdx = ny * 8 + nx
      l.io.nbrIn(n) := laneMods(nIdx).io.nbrOut
    }
  }

  val loadStore = Module(new MemLoadStore)

  val sIdle :: sMemReq :: sMemWait :: Nil = Enum(3)
  val memState = RegInit(sIdle)
  val memLaneIdx = RegInit(0.U(6.W))
  val memIsStore = Reg(Bool())
  val memIsGlobal = Reg(Bool())
  val totalLanes = 64

  val currLane = laneIOs(memLaneIdx)
  val anyLaneReq = laneMods.map(_.io.memReq).reduce(_ || _)

  loadStore.io.laneReq.valid := false.B
  loadStore.io.laneReq.bits.isGlobal := memIsGlobal
  loadStore.io.laneReq.bits.isShared := !memIsGlobal
  loadStore.io.laneReq.bits.isStore := memIsStore
  loadStore.io.laneReq.bits.addr := currLane.memAddr
  loadStore.io.laneReq.bits.data := currLane.memData

  val memDone = Wire(Bool())
  scheduler.io.memDone := memDone

  laneMods.foreach { l =>
    l.io.memRespValid := false.B
    l.io.memRespData := 0.U
  }

  switch(memState) {
    is(sIdle) {
      memLaneIdx := 0.U
      when(anyLaneReq) {
        memState := sMemReq
      }
    }

    is(sMemReq) {
      loadStore.io.laneReq.valid := true.B
      memIsStore := currLane.memStore
      memIsGlobal := currLane.memGlobal
      memState := sMemWait
    }

    is(sMemWait) {
      val laneDone = memIsStore || loadStore.io.laneResp.valid
      when(laneDone) {
        when(!memIsStore) {
          currLane.memRespData := loadStore.io.laneResp.bits.data
          currLane.memRespValid := true.B
        }
        when(memLaneIdx === (totalLanes - 1).U) {
          memState := sIdle
          memDone := true.B
        }.otherwise {
          memLaneIdx := memLaneIdx + 1.U
          memState := sMemReq
        }
      }
    }
  }

  io.memAddr := loadStore.io.global.addr
  io.memWrData := loadStore.io.global.wdata
  io.memReq := loadStore.io.global.en
  io.memWr := loadStore.io.global.wr
  loadStore.io.global.rdata := io.memRespData
  loadStore.io.global.rvalid := io.memRespValid
  loadStore.io.global.ready := true.B
}
