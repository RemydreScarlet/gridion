package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PositDecodeEncode(p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(p.N.W))
    val out = Output(UInt(p.N.W))
  })
  val dec = Module(new PositDecode(p))
  val enc = Module(new PositEncode(p))
  dec.io.in := io.in
  enc.io.in := dec.io.out
  io.out := enc.io.out
}

class PositDecodeTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = PositParams()

  behavior of "PositDecode"

  it should "decode zero" in {
    test(new PositDecode(p)) { c =>
      c.io.in.poke(0.U)
      c.clock.step(1)
      c.io.out.isZero.expect(true.B)
      c.io.out.isNaR.expect(false.B)
    }
  }

  it should "decode NaR" in {
    test(new PositDecode(p)) { c =>
      c.io.in.poke(0x8000.U)
      c.clock.step(1)
      c.io.out.isZero.expect(false.B)
      c.io.out.isNaR.expect(true.B)
    }
  }

  it should "decode one and roundtrip" in {
    test(new PositDecodeEncode(p)) { c =>
      c.io.in.poke(0x4000.U)
      c.clock.step(1)
      val out = c.io.out.peekInt()
      assert(out == 0x4000L, s"one roundtrip: $out != 0x4000")
    }
  }

  it should "roundtrip 0x2000" in {
    test(new PositDecodeEncode(p)) { c =>
      c.io.in.poke(0x2000.U)
      c.clock.step(1)
      val out = c.io.out.peekInt()
      assert(out == 0x2000L, s"roundtrip 0x2000: $out != 0x2000")
    }
  }

  it should "roundtrip 0x7FFF" in {
    test(new PositDecodeEncode(p)) { c =>
      c.io.in.poke(0x7FFF.U)
      c.clock.step(1)
      val out = c.io.out.peekInt()
      assert(out == 0x7FFFL, s"roundtrip 0x7FFF: $out != 0x7FFF")
    }
  }
}
