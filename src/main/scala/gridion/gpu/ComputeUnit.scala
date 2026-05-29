package gridion.gpu

import chisel3._
import chisel3.util._
import gridion.posit._
import gridion.gpu.simt._
import gridion.gpu.memory._

class ComputeUnit(val p: PositParams = PositParams(), val numWarps: Int = 4, val microcodeSize: Int = 4096, val numLanes: Int = 64, val addTestHarness: Boolean = false) extends Module {
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

    val test = if (addTestHarness) Some(new Bundle {
      val lane = new Bundle {
        val wrEn = Input(Bool())
        val wrAddr = Input(UInt(4.W))
        val wrData = Input(UInt(p.N.W))
        val rdAddr = Input(UInt(4.W))
        val rdData = Output(UInt(p.N.W))
      }
    }) else None
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

  val laneMods = Seq.fill(numLanes)(Module(new SIMTLane(p, addTestHarness)))

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
      if (nIdx < numLanes) {
        l.io.nbrIn(n) := laneMods(nIdx).io.nbrOut
      } else {
        l.io.nbrIn(n) := 0.U
      }
    }
  }

  val loadStore = Module(new MemLoadStore)

  val sIdle :: sMemReq :: sMemWait :: sNextLane :: Nil = Enum(4)
  val memState = RegInit(sIdle)
  val memLaneIdx = RegInit(0.U(log2Ceil(numLanes).W))
  val memIsStore = Wire(Bool())
  val memIsGlobal = Wire(Bool())
  val memReqAddr = Wire(UInt(32.W))
  val memReqData = Wire(UInt(16.W))
  val totalLanes = numLanes

  val laneReqVec = VecInit(laneMods.map(_.io.memReq))
  val anyLaneReq = laneReqVec.reduce(_ || _)
  val laneReqIdx = PriorityEncoder(laneReqVec.asUInt)

  memIsStore := false.B
  memIsGlobal := false.B
  memReqAddr := 0.U
  memReqData := 0.U
  loadStore.io.laneReq.valid := false.B
  loadStore.io.laneReq.bits.isGlobal := memIsGlobal
  loadStore.io.laneReq.bits.isShared := !memIsGlobal
  loadStore.io.laneReq.bits.isStore := memIsStore
  loadStore.io.laneReq.bits.addr := memReqAddr
  loadStore.io.laneReq.bits.data := memReqData

  val memDone = Wire(Bool())
  memDone := false.B
  scheduler.io.memDone := memDone

  laneMods.foreach { l =>
    l.io.memRespValid := false.B
    l.io.memRespData := 0.U
  }

  switch(memState) {
    is(sIdle) {
      memLaneIdx := laneReqIdx
      when(anyLaneReq) {
        memState := sMemReq
      }
    }

    is(sMemReq) {
      for (i <- 0 until totalLanes) {
        when(memLaneIdx === i.U) {
          when(laneMods(i).io.memReq) {
            memIsStore := laneMods(i).io.memStore
            memIsGlobal := laneMods(i).io.memGlobal
            memReqAddr := laneMods(i).io.memAddr
            memReqData := laneMods(i).io.memData
            loadStore.io.laneReq.valid := true.B
            memState := sMemWait
          }.otherwise {
            memState := sNextLane
          }
        }
      }
    }

    is(sMemWait) {
      when(loadStore.io.laneResp.valid) {
        for (i <- 0 until totalLanes) {
          when(memLaneIdx === i.U) {
            laneMods(i).io.memRespValid := true.B
            when(!memIsStore) {
              laneMods(i).io.memRespData := loadStore.io.laneResp.bits.data
            }
          }
        }
        memState := sNextLane
      }
    }

    is(sNextLane) {
      when(memLaneIdx === (totalLanes - 1).U) {
        memState := sIdle
        memDone := true.B
      }.otherwise {
        memLaneIdx := memLaneIdx + 1.U
        memState := sMemReq
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

  if (addTestHarness) {
    val th = io.test.get
    val lane0 = laneMods(0)
    lane0.io.test.get.wrEn := th.lane.wrEn
    lane0.io.test.get.wrAddr := th.lane.wrAddr
    lane0.io.test.get.wrData := th.lane.wrData
    lane0.io.test.get.rdAddr := th.lane.rdAddr
    th.lane.rdData := lane0.io.test.get.rdData

    for (i <- 1 until numLanes) {
      laneMods(i).io.test.get.wrEn := false.B
      laneMods(i).io.test.get.wrAddr := 0.U
      laneMods(i).io.test.get.wrData := 0.U
      laneMods(i).io.test.get.rdAddr := 0.U
    }
  }
}
