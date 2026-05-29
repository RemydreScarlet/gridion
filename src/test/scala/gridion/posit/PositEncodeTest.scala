package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PositEncodeWrapper(p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val sign = Input(Bool())
    val exp = Input(SInt(p.maxExpBits.W))
    val sig = Input(UInt(p.sigWidth.W))
    val isZero = Input(Bool())
    val isNaR = Input(Bool())
    val out = Output(UInt(p.N.W))
  })
  val internal = Wire(new PositInternal(p))
  internal.sign := io.sign
  internal.exp := io.exp
  internal.sig := io.sig
  internal.isZero := io.isZero
  internal.isNaR := io.isNaR
  val enc = Module(new PositEncode(p))
  enc.io.in := internal
  io.out := enc.io.out
}

class PositEncodeTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = PositParams()

  behavior of "PositEncode"

  it should "encode zero" in {
    test(new PositEncodeWrapper(p)) { c =>
      c.io.sign.poke(false.B)
      c.io.isZero.poke(true.B)
      c.io.isNaR.poke(false.B)
      c.io.exp.poke(0.S)
      c.io.sig.poke(0.U)
      c.clock.step(1)
      c.io.out.expect(0x0000.U)
    }
  }

  it should "encode NaR" in {
    test(new PositEncodeWrapper(p)) { c =>
      c.io.sign.poke(false.B)
      c.io.isZero.poke(false.B)
      c.io.isNaR.poke(true.B)
      c.io.exp.poke(0.S)
      c.io.sig.poke(0.U)
      c.clock.step(1)
      c.io.out.expect(0x8000.U)
    }
  }

  it should "encode posit 1.0 (0x4000)" in {
    test(new PositEncodeWrapper(p)) { c =>
      c.io.sign.poke(false.B)
      c.io.isZero.poke(false.B)
      c.io.isNaR.poke(false.B)
      c.io.exp.poke(0.S)
      c.io.sig.poke("h800000".U(24.W))
      c.clock.step(1)
      c.io.out.expect(0x4000.U)
    }
  }

  it should "encode posit 2.0 (0x5000)" in {
    test(new PositEncodeWrapper(p)) { c =>
      c.io.sign.poke(false.B)
      c.io.isZero.poke(false.B)
      c.io.isNaR.poke(false.B)
      c.io.exp.poke(1.S)
      c.io.sig.poke("h800000".U(24.W))
      c.clock.step(1)
      c.io.out.expect(0x5000.U)
    }
  }

  it should "encode negative posit -1.0 (0xC000)" in {
    test(new PositEncodeWrapper(p)) { c =>
      c.io.sign.poke(true.B)
      c.io.isZero.poke(false.B)
      c.io.isNaR.poke(false.B)
      c.io.exp.poke(0.S)
      c.io.sig.poke("h800000".U(24.W))
      c.clock.step(1)
      c.io.out.expect(0xC000.U)
    }
  }

  it should "encode max positive value" in {
    test(new PositEncodeWrapper(p)) { c =>
      c.io.sign.poke(false.B)
      c.io.isZero.poke(false.B)
      c.io.isNaR.poke(false.B)
      c.io.exp.poke(28.S)
      c.io.sig.poke("h001000".U(24.W))
      c.clock.step(1)
      c.io.out.expect(0x7FFF.U)
    }
  }
}
