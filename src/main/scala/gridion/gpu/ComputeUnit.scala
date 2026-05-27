package gridion.gpu

import chisel3._
import chisel3.util._
import gridion.posit._
import gridion.gpu.simt._

class ComputeUnit(val p: PositParams = PositParams(), val numWarps: Int = 4, val microcodeSize: Int = 4096) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val kernelAddr = Input(UInt(12.W))
    val microcodeData = Input(UInt(16.W))
    val microcodeWrEn = Input(Bool())
    val microcodeWrAddr = Input(UInt(12.W))

    val done = Output(Bool())

    val memAddr = Output(UInt(32.W))
    val memWrData = Output(UInt(16.W))
    val memReq = Output(Bool())
    val memWr = Output(Bool())
    val memRespData = Input(UInt(16.W))
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

  val lanes = Seq.fill(8, 8)(Module(new SIMTLane(p)))

  for (x <- 0 until 8) {
    for (y <- 0 until 8) {
      lanes(x)(y).io.opcode := scheduler.io.laneOpcode
      lanes(x)(y).io.dst := scheduler.io.laneDst
      lanes(x)(y).io.src1 := scheduler.io.laneSrc1
      lanes(x)(y).io.src2 := scheduler.io.laneSrc2
      lanes(x)(y).io.dx := scheduler.io.laneDx
      lanes(x)(y).io.dy := scheduler.io.laneDy
      lanes(x)(y).io.x := x.U
      lanes(x)(y).io.y := y.U
      lanes(x)(y).io.issue := scheduler.io.laneIssue
      lanes(x)(y).io.commit := scheduler.io.laneCommit
    }
  }

  val neighborOffsets = Seq(
    (1, -1), (0, -1), (-1, -1),
    (1,  0),          (-1,  0),
    (1,  1), (0,  1), (-1,  1)
  )

  for (x <- 0 until 8) {
    for (y <- 0 until 8) {
      for (n <- 0 until 8) {
        val (dx, dy) = neighborOffsets(n)
        val nx = (x + dx + 8) % 8
        val ny = (y + dy + 8) % 8
        lanes(x)(y).io.nbrIn(n) := lanes(nx)(ny).io.nbrOut
      }
    }
  }

  io.memAddr := 0.U
  io.memWrData := 0.U
  io.memReq := false.B
  io.memWr := false.B
}
