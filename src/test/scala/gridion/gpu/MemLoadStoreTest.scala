package gridion.gpu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import gridion.gpu.memory.MemLoadStore

class MemLoadStoreTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MemLoadStore"

  it should "store to shared memory" in {
    test(new MemLoadStore) { c =>
      c.io.global.rvalid.poke(false.B)

      c.io.laneReq.valid.poke(true.B)
      c.io.laneReq.bits.addr.poke(0x4000_0000L.U)
      c.io.laneReq.bits.data.poke(0x1234.U)
      c.io.laneReq.bits.isStore.poke(true.B)
      c.io.laneReq.bits.isGlobal.poke(false.B)
      c.io.laneReq.bits.isShared.poke(true.B)
      c.clock.step(1)
      c.io.laneReq.valid.poke(false.B)
      c.clock.step(1)

      assert(c.io.laneResp.valid.peekBoolean(), "store should complete after 2 cycles")
      c.clock.step(1)
    }
  }

  it should "load from shared memory" in {
    test(new MemLoadStore) { c =>
      c.io.global.rvalid.poke(false.B)

      c.io.laneReq.valid.poke(true.B)
      c.io.laneReq.bits.addr.poke(0x4000_0000L.U)
      c.io.laneReq.bits.data.poke(0xABCD.U)
      c.io.laneReq.bits.isStore.poke(true.B)
      c.io.laneReq.bits.isGlobal.poke(false.B)
      c.io.laneReq.bits.isShared.poke(true.B)
      c.clock.step(1)
      c.io.laneReq.valid.poke(false.B)
      c.clock.step(1)

      assert(c.io.laneResp.valid.peekBoolean(), "store should complete")
      c.clock.step(1)

      c.io.laneReq.valid.poke(true.B)
      c.io.laneReq.bits.addr.poke(0x4000_0000L.U)
      c.io.laneReq.bits.isStore.poke(false.B)
      c.io.laneReq.bits.isGlobal.poke(false.B)
      c.io.laneReq.bits.isShared.poke(true.B)
      c.clock.step(1)
      c.io.laneReq.valid.poke(false.B)
      c.clock.step(1)

      assert(c.io.laneResp.valid.peekBoolean(), "shared load should complete")
      val data = c.io.laneResp.bits.data.peekInt()
      assert(data == 0xABCD, s"shared load data: 0x${data.toString(16)} != 0xABCD")

      c.clock.step(1)
    }
  }

  it should "request global memory load" in {
    test(new MemLoadStore) { c =>
      c.io.laneReq.valid.poke(true.B)
      c.io.laneReq.bits.addr.poke(0x1000_0000L.U)
      c.io.laneReq.bits.data.poke(0.U)
      c.io.laneReq.bits.isStore.poke(false.B)
      c.io.laneReq.bits.isGlobal.poke(true.B)
      c.io.laneReq.bits.isShared.poke(false.B)
      c.io.global.rvalid.poke(false.B)
      c.clock.step(1)

      c.io.laneReq.valid.poke(false.B)

      assert(c.io.global.en.peekBoolean(), "global bus should be requested")
      assert(!c.io.global.wr.peekBoolean(), "global should not be a write")
      assert(c.io.global.addr.peekInt() == 0x1000_0000L, "global addr should match")

      c.clock.step(1)
    }
  }

  it should "complete global memory load with response" in {
    test(new MemLoadStore) { c =>
      c.io.laneReq.valid.poke(true.B)
      c.io.laneReq.bits.addr.poke(0x1000_0000L.U)
      c.io.laneReq.bits.data.poke(0.U)
      c.io.laneReq.bits.isStore.poke(false.B)
      c.io.laneReq.bits.isGlobal.poke(true.B)
      c.io.laneReq.bits.isShared.poke(false.B)
      c.io.global.rvalid.poke(false.B)
      c.clock.step(1)
      c.io.laneReq.valid.poke(false.B)

      c.io.global.rvalid.poke(true.B)
      c.io.global.rdata.poke(0x7FFF.U)

      assert(c.io.laneResp.valid.peekBoolean(), "global load resp should be valid before step")
      val data = c.io.laneResp.bits.data.peekInt()
      assert(data == 0x7FFF, s"global load data: 0x${data.toString(16)} != 0x7FFF")

      c.io.global.rvalid.poke(false.B)
      c.clock.step(1)
    }
  }

  it should "request global memory store" in {
    test(new MemLoadStore) { c =>
      c.io.laneReq.valid.poke(true.B)
      c.io.laneReq.bits.addr.poke(0x2000_0000L.U)
      c.io.laneReq.bits.data.poke(0xDEAD.U)
      c.io.laneReq.bits.isStore.poke(true.B)
      c.io.laneReq.bits.isGlobal.poke(true.B)
      c.io.laneReq.bits.isShared.poke(false.B)
      c.io.global.rvalid.poke(false.B)
      c.clock.step(1)
      c.io.laneReq.valid.poke(false.B)

      assert(c.io.global.en.peekBoolean(), "global bus should be enabled")
      assert(c.io.global.wr.peekBoolean(), "global should be a write")
      assert(c.io.global.addr.peekInt() == 0x2000_0000L, "global addr should match")
      assert(c.io.global.wdata.peekInt() == 0xDEAD, "global wdata should match")

      c.clock.step(1)
    }
  }
}
