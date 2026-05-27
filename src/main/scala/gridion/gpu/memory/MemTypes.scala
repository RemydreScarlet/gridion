package gridion.gpu.memory

import chisel3._

class MemBusIO extends Bundle {
  val addr = Output(UInt(32.W))
  val wdata = Output(UInt(64.W))
  val rdata = Input(UInt(64.W))
  val en = Output(Bool())
  val wr = Output(Bool())
  val ready = Input(Bool())
  val rvalid = Input(Bool())
}

class LaneMemReq extends Bundle {
  val addr = UInt(32.W)
  val data = UInt(16.W)
  val isStore = Bool()
  val isGlobal = Bool()
  val isShared = Bool()
}

class LaneMemResp extends Bundle {
  val data = UInt(16.W)
}

object AddressSpace {
  val GLOBAL_START = 0x00000000L
  val GLOBAL_END   = 0x3FFFFFFFL
  val SHARED_START = 0x40000000L
  val SHARED_END   = 0x40003FFFL

  def isGlobal(addr: UInt): Bool = addr <= GLOBAL_END.U
  def isShared(addr: UInt): Bool = addr >= SHARED_START.U && addr <= SHARED_END.U
}
