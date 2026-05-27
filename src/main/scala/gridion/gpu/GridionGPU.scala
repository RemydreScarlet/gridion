package gridion.gpu

import chisel3._
import gridion.posit._

class GridionGPU(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val kernelAddr = Input(UInt(12.W))
    val microcodeWrEn = Input(Bool())
    val microcodeWrAddr = Input(UInt(12.W))
    val microcodeData = Input(UInt(16.W))
    val done = Output(Bool())

    val memAddr = Output(UInt(32.W))
    val memWrData = Output(UInt(64.W))
    val memReq = Output(Bool())
    val memWr = Output(Bool())
    val memRespData = Input(UInt(64.W))
    val memRespValid = Input(Bool())
  })

  val cu = Module(new ComputeUnit(p))
  cu.io.start := io.start
  cu.io.kernelAddr := io.kernelAddr
  cu.io.microcodeWrEn := io.microcodeWrEn
  cu.io.microcodeWrAddr := io.microcodeWrAddr
  cu.io.microcodeData := io.microcodeData

  io.done := cu.io.done
  io.memAddr := cu.io.memAddr
  io.memWrData := cu.io.memWrData
  io.memReq := cu.io.memReq
  io.memWr := cu.io.memWr
  cu.io.memRespData := io.memRespData
  cu.io.memRespValid := io.memRespValid
}

object GridionGPU extends App {
  emitVerilog(new GridionGPU(), Array("--target-dir", "generated"))
}
