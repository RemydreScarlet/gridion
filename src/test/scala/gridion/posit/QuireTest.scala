package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class QuireEndToEnd(p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(p.N.W))
    val b = Input(UInt(p.N.W))
    val clr = Input(Bool())
    val acc = Input(Bool())
    val rnd = Input(Bool())
    val out = Output(UInt(p.N.W))
  })
  val decA = Module(new PositDecode(p))
  val decB = Module(new PositDecode(p))
  val quire = Module(new Quire(p))
  val enc = Module(new PositEncode(p))
  decA.io.in := io.a
  decB.io.in := io.b
  quire.io.clr := io.clr
  quire.io.acc := io.acc
  quire.io.rnd := io.rnd
  quire.io.a := decA.io.out
  quire.io.b := decB.io.out
  enc.io.in := quire.io.result
  io.out := enc.io.out
}

class QuireTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = PositParams()

  behavior of "Quire"

  it should "clear and round to zero" in {
    test(new QuireEndToEnd(p)) { c =>
      c.io.a.poke(0x4000.U)
      c.io.b.poke(0x4000.U)
      c.io.clr.poke(true.B)
      c.io.acc.poke(false.B)
      c.io.rnd.poke(true.B)
      c.clock.step(1)
      c.io.out.expect(0x0000.U)
    }
  }

  it should "accumulate 1.0 * 2.0 = 2.0" in {
    test(new QuireEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(1.0f)
      val b = SoftPosit.fromFloat(2.0f)
      val expected = SoftPosit.fromFloat(2.0f)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.io.clr.poke(true.B)
      c.io.acc.poke(false.B)
      c.io.rnd.poke(false.B)
      c.clock.step(1)

      c.io.clr.poke(false.B)
      c.io.acc.poke(true.B)
      c.clock.step(1)

      c.io.acc.poke(false.B)
      c.io.rnd.poke(true.B)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val refF = SoftPosit.toFloat(expected)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.01f, s"1.0 * 2.0 = $result ($resF) vs $expected ($refF)")
    }
  }

  it should "accumulate multiple products: 1*2 + 3*4 = 14" in {
    test(new QuireEndToEnd(p)) { c =>
      val a1 = SoftPosit.fromFloat(1.0f)
      val b1 = SoftPosit.fromFloat(2.0f)
      val a2 = SoftPosit.fromFloat(3.0f)
      val b2 = SoftPosit.fromFloat(4.0f)
      val expected = SoftPosit.fromFloat(14.0f)

      c.io.clr.poke(true.B)
      c.io.acc.poke(false.B)
      c.io.rnd.poke(false.B)
      c.clock.step(1)

      c.io.clr.poke(false.B)
      c.io.a.poke(a1.U)
      c.io.b.poke(b1.U)
      c.io.acc.poke(true.B)
      c.clock.step(1)

      c.io.a.poke(a2.U)
      c.io.b.poke(b2.U)
      c.io.acc.poke(true.B)
      c.clock.step(1)

      c.io.acc.poke(false.B)
      c.io.rnd.poke(true.B)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val refF = SoftPosit.toFloat(expected)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.10f, s"1*2 + 3*4 = $result ($resF) vs $expected ($refF)")
    }
  }

  it should "accumulate negative product: -1.0 * 2.0 = -2.0" in {
    test(new QuireEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(-1.0f)
      val b = SoftPosit.fromFloat(2.0f)
      val expected = SoftPosit.fromFloat(-2.0f)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.io.clr.poke(true.B)
      c.io.acc.poke(false.B)
      c.io.rnd.poke(false.B)
      c.clock.step(1)

      c.io.clr.poke(false.B)
      c.io.acc.poke(true.B)
      c.clock.step(1)

      c.io.acc.poke(false.B)
      c.io.rnd.poke(true.B)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val refF = SoftPosit.toFloat(expected)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.01f, s"-1.0 * 2.0 = $result ($resF) vs $expected ($refF)")
    }
  }

  it should "accumulate product with zero results in zero" in {
    test(new QuireEndToEnd(p)) { c =>
      c.io.clr.poke(true.B)
      c.io.acc.poke(true.B)
      c.io.rnd.poke(false.B)
      c.io.a.poke(SoftPosit.fromFloat(0.0f).U)
      c.io.b.poke(SoftPosit.fromFloat(5.0f).U)
      c.clock.step(1)

      c.io.clr.poke(false.B)
      c.io.acc.poke(false.B)
      c.io.rnd.poke(true.B)
      c.clock.step(1)

      c.io.out.expect(0x0000.U)
    }
  }
}
