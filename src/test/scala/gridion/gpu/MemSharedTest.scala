package gridion.gpu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import gridion.gpu.memory.MemShared

class MemSharedTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MemShared"

  it should "write and read back a value" in {
    test(new MemShared) { c =>
      val testData = BigInt("DEADBEEFCAFEBABE", 16)
      c.io.addr.poke(0x4000_0000L.U)
      c.io.wdata.poke(testData.U(64.W))
      c.io.en.poke(true.B)
      c.io.wr.poke(true.B)
      c.clock.step(1)

      c.io.wr.poke(false.B)
      c.clock.step(1)

      c.io.addr.poke(0x4000_0000L.U)
      c.io.en.poke(true.B)
      c.clock.step(2)

      val data = c.io.rdata.peekInt()
      assert(data == testData, s"read: 0x${data.toString(16)} != 0xdeadbeefcafebabe")
    }
  }

  it should "read zero from uninitialized address" in {
    test(new MemShared) { c =>
      c.io.addr.poke(0x4000_0008L.U)
      c.io.en.poke(true.B)
      c.io.wr.poke(false.B)
      c.clock.step(1)

      val data = c.io.rdata.peekInt()
      assert(data == 0, s"uninitialized read: $data != 0")
    }
  }

  it should "write different values to different addresses" in {
    test(new MemShared) { c =>
      c.io.addr.poke(0x4000_0000L.U)
      c.io.wdata.poke(BigInt("1111111111111111", 16).U(64.W))
      c.io.en.poke(true.B)
      c.io.wr.poke(true.B)
      c.clock.step(1)

      c.io.addr.poke(0x4000_0008L.U)
      c.io.wdata.poke(BigInt("2222222222222222", 16).U(64.W))
      c.io.en.poke(true.B)
      c.io.wr.poke(true.B)
      c.clock.step(1)

      c.io.addr.poke(0x4000_0000L.U)
      c.io.wr.poke(false.B)
      c.io.en.poke(true.B)
      c.clock.step(2)

      val data1 = c.io.rdata.peekInt()
      assert(data1 == BigInt("1111111111111111", 16), s"addr0: 0x${data1.toString(16)}")

      c.io.addr.poke(0x4000_0008L.U)
      c.io.en.poke(true.B)
      c.clock.step(2)

      val data2 = c.io.rdata.peekInt()
      assert(data2 == BigInt("2222222222222222", 16), s"addr8: 0x${data2.toString(16)}")
    }
  }
}
