package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PositMulEndToEnd(p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(p.N.W))
    val b = Input(UInt(p.N.W))
    val out = Output(UInt(p.N.W))
  })
  val decA = Module(new PositDecode(p))
  val decB = Module(new PositDecode(p))
  val mul = Module(new PositMul(p))
  val enc = Module(new PositEncode(p))
  decA.io.in := io.a
  decB.io.in := io.b
  mul.io.a := decA.io.out
  mul.io.b := decB.io.out
  enc.io.in := mul.io.out
  io.out := enc.io.out
}

class PositMulTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = PositParams()

  behavior of "PositMul"

  it should "multiply 2 by 3" in {
    test(new PositMulEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(2.0f)
      val b = SoftPosit.fromFloat(3.0f)
      val ref = SoftPosit.mul(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.01f, s"2 * 3 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "multiply 1.5 by 2.0" in {
    test(new PositMulEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(1.5f)
      val b = SoftPosit.fromFloat(2.0f)
      val ref = SoftPosit.mul(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.01f, s"1.5 * 2.0 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "propagate NaR" in {
    test(new PositMulEndToEnd(p)) { c =>
      c.io.a.poke(0x8000.U)
      c.io.b.poke(0x4000.U)
      c.clock.step(1)
      c.io.out.expect(0x8000.U)
    }
  }

  it should "multiply by zero" in {
    test(new PositMulEndToEnd(p)) { c =>
      c.io.a.poke(0x0000.U)
      c.io.b.poke(0x5000.U)
      c.clock.step(1)
      c.io.out.expect(0x0000.U)
    }
  }

  it should "multiply negative * negative = positive" in {
    test(new PositMulEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(-2.0f)
      val b = SoftPosit.fromFloat(-3.0f)
      val ref = SoftPosit.mul(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.01f, s"-2 * -3 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "multiply negative * positive = negative" in {
    test(new PositMulEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(-1.5f)
      val b = SoftPosit.fromFloat(2.0f)
      val ref = SoftPosit.mul(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.01f, s"-1.5 * 2.0 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "multiply by 1.0 (identity)" in {
    test(new PositMulEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(1.0f)
      val b = SoftPosit.fromFloat(7.5f)
      val ref = SoftPosit.mul(a, b)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      assert(result == ref, s"1 * 7.5 = $result vs $ref")
    }
  }

  it should "multiply large values close to max" in {
    test(new PositMulEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(128.0f)
      val b = SoftPosit.fromFloat(128.0f)
      val ref = SoftPosit.mul(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"128 * 128 = $result ($resF) vs $ref ($refF)")
    }
  }
}
