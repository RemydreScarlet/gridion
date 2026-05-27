package gridion.gpu.memory

import chisel3._

class MemShared extends Module {
  val io = IO(new MemBusIO)

  val mem = SyncReadMem(2048, UInt(64.W))

  val addr = io.addr(13, 3)
  val rdata = mem.read(addr, io.en)

  when(io.en && io.wr) {
    mem.write(addr, io.wdata)
  }

  io.rdata := rdata
  io.ready := true.B
  io.rvalid := io.en && !io.wr
}
