package gridion.gpu.memory

import chisel3._
import chisel3.util._

class MemLoadStore extends Module {
  val io = IO(new Bundle {
    val laneReq = Flipped(Valid(new LaneMemReq))
    val laneResp = Valid(new LaneMemResp)

    val global = new MemBusIO
  })

  val sIdle :: sSharedWait :: sGlobalWait :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val reqReg = Reg(new LaneMemReq)

  val sharedAddr = reqReg.addr(13, 3)
  val sharedOffset = reqReg.addr(2, 0)

  val sharedWdata = Wire(UInt(64.W))
  sharedWdata := reqReg.data << (sharedOffset << 4)

  val sharedRdata = Wire(UInt(64.W))
  val sharedRdataSel = (sharedRdata >> (sharedOffset << 4))(15, 0)

  val shared = Module(new MemShared)

  io.laneResp.valid := false.B
  io.laneResp.bits.data := 0.U

  shared.io.addr := sharedAddr
  shared.io.wdata := sharedWdata
  shared.io.en := state === sSharedWait || (state === sIdle && io.laneReq.valid && reqReg.isShared)
  shared.io.wr := reqReg.isStore && (state === sSharedWait || (state === sIdle && io.laneReq.valid))

  io.global.addr := reqReg.addr
  io.global.wdata := Cat(Fill(48, 0.U), reqReg.data)
  io.global.en := state === sGlobalWait && io.global.ready
  io.global.wr := reqReg.isStore

  switch(state) {
    is(sIdle) {
      when(io.laneReq.valid) {
        reqReg := io.laneReq.bits
        when(io.laneReq.bits.isShared) {
          when(io.laneReq.bits.isStore) {
            state := sIdle
            io.laneResp.valid := true.B
          }.otherwise {
            state := sSharedWait
          }
        }.otherwise {
          state := sGlobalWait
        }
      }
    }

    is(sSharedWait) {
      io.laneResp.valid := true.B
      io.laneResp.bits.data := sharedRdataSel
      state := sIdle
    }

    is(sGlobalWait) {
      io.global.en := true.B
      when(io.global.rvalid) {
        io.laneResp.valid := true.B
        io.laneResp.bits.data := io.global.rdata(15, 0)
        state := sIdle
      }
    }
  }

  sharedRdata := shared.io.rdata
}
