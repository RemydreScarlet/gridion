package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PositAddEndToEnd(p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(p.N.W))
    val b = Input(UInt(p.N.W))
    val out = Output(UInt(p.N.W))
  })
  val decA = Module(new PositDecode(p))
  val decB = Module(new PositDecode(p))
  val add = Module(new PositAdd(p))
  val enc = Module(new PositEncode(p))
  decA.io.in := io.a
  decB.io.in := io.b
  add.io.a := decA.io.out
  add.io.b := decB.io.out
  enc.io.in := add.io.out
  io.out := enc.io.out
}

class PositAddTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = PositParams()

  behavior of "PositAdd"

  it should "add 1 + 2" in {
    test(new PositAddEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(1.0f)
      val b = SoftPosit.fromFloat(2.0f)
      val ref = SoftPosit.add(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"1 + 2 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "add 1.5 + 2.5" in {
    test(new PositAddEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(1.5f)
      val b = SoftPosit.fromFloat(2.5f)
      val ref = SoftPosit.add(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"1.5 + 2.5 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "propagate NaR" in {
    test(new PositAddEndToEnd(p)) { c =>
      c.io.a.poke(0x8000.U)
      c.io.b.poke(0x4000.U)
      c.clock.step(1)
      c.io.out.expect(0x8000.U)
    }
  }

  it should "add zero to zero" in {
    test(new PositAddEndToEnd(p)) { c =>
      c.io.a.poke(0x0000.U)
      c.io.b.poke(0x0000.U)
      c.clock.step(1)
      c.io.out.expect(0x0000.U)
    }
  }

  it should "add positive to zero" in {
    test(new PositAddEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(0.0f)
      val b = SoftPosit.fromFloat(3.0f)
      val ref = SoftPosit.add(a, b)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      assert(result == ref, s"0 + 3 = $result vs $ref")
    }
  }

  it should "add negative values: -1 + -2 = -3" in {
    test(new PositAddEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(-1.0f)
      val b = SoftPosit.fromFloat(-2.0f)
      val ref = SoftPosit.add(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"-1 + -2 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "add positive and negative: 3 + -1 = 2" in {
    test(new PositAddEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(3.0f)
      val b = SoftPosit.fromFloat(-1.0f)
      val ref = SoftPosit.add(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"3 + -1 = $result ($resF) vs $ref ($refF)")
    }
  }

  it should "add values with large exponent difference" in {
    test(new PositAddEndToEnd(p)) { c =>
      val a = SoftPosit.fromFloat(1.0f)
      val b = SoftPosit.fromFloat(256.0f)
      val ref = SoftPosit.add(a, b)
      val refF = SoftPosit.toFloat(ref)

      c.io.a.poke(a.U)
      c.io.b.poke(b.U)
      c.clock.step(1)

      val result = c.io.out.peekInt().toInt
      val resF = SoftPosit.toFloat(result)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"1 + 256 = $result ($resF) vs $ref ($refF)")
    }
  }
}
