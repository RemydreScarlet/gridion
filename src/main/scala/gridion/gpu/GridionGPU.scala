package gridion.gpu

import chisel3._
import chisel3.util._
import gridion.posit._
import gridion.gpu.simt._

class GridionGPU(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val kernelAddr = Input(UInt(12.W))
    val microcodeWrEn = Input(Bool())
    val microcodeWrAddr = Input(UInt(12.W))
    val microcodeData = Input(UInt(16.W))
    val done = Output(Bool())
  })

  val cu = Module(new ComputeUnit(p))
  cu.io.start := io.start
  cu.io.kernelAddr := io.kernelAddr
  cu.io.microcodeWrEn := io.microcodeWrEn
  cu.io.microcodeWrAddr := io.microcodeWrAddr
  cu.io.microcodeData := io.microcodeData

  io.done := cu.io.done
}

object GridionGPU extends App {
  emitVerilog(new GridionGPU(), Array("--target-dir", "generated"))
}
