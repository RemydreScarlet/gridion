package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PositCMPEndToEnd(p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(p.N.W))
    val b = Input(UInt(p.N.W))
    val eq = Output(Bool())
    val ne = Output(Bool())
    val lt = Output(Bool())
    val le = Output(Bool())
    val gt = Output(Bool())
    val ge = Output(Bool())
  })
  val decA = Module(new PositDecode(p))
  val decB = Module(new PositDecode(p))
  val cmp = Module(new PositCMP(p))
  decA.io.in := io.a
  decB.io.in := io.b
  cmp.io.a := decA.io.out
  cmp.io.b := decB.io.out
  io.eq := cmp.io.eq
  io.ne := cmp.io.ne
  io.lt := cmp.io.lt
  io.le := cmp.io.le
  io.gt := cmp.io.gt
  io.ge := cmp.io.ge
}

class PositCMPTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = PositParams()

  behavior of "PositCMP"

  it should "compare equal positive values" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x4000.U)
      c.io.b.poke(0x4000.U)
      c.clock.step(1)
      c.io.eq.expect(true.B)
      c.io.ne.expect(false.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(true.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(true.B)
    }
  }

  it should "compare a > b (positive)" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x5000.U)  // 2.0
      c.io.b.poke(0x4000.U)  // 1.0
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(false.B)
      c.io.gt.expect(true.B)
      c.io.ge.expect(true.B)
    }
  }

  it should "compare a < b (positive)" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x4000.U)  // 1.0
      c.io.b.poke(0x5000.U)  // 2.0
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(true.B)
      c.io.le.expect(true.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(false.B)
    }
  }

  it should "compare a > b (negative)" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0xB000.U)  // -2.0
      c.io.b.poke(0xC000.U)  // -1.0
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(true.B)
      c.io.le.expect(true.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(false.B)
    }
  }

  it should "compare a < b (negative)" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0xC000.U)  // -1.0
      c.io.b.poke(0xB000.U)  // -2.0
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(false.B)
      c.io.gt.expect(true.B)
      c.io.ge.expect(true.B)
    }
  }

  it should "compare zero with zero" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x0000.U)
      c.io.b.poke(0x0000.U)
      c.clock.step(1)
      c.io.eq.expect(true.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(true.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(true.B)
    }
  }

  it should "compare positive with zero" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x4000.U)  // 1.0
      c.io.b.poke(0x0000.U)
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(false.B)
      c.io.gt.expect(true.B)
      c.io.ge.expect(true.B)
    }
  }

  it should "compare zero with positive" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x0000.U)
      c.io.b.poke(0x4000.U)  // 1.0
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(true.B)
      c.io.le.expect(true.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(false.B)
    }
  }

  it should "compare NaR with anything yields ne only" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x8000.U)
      c.io.b.poke(0x4000.U)
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.ne.expect(true.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(false.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(false.B)
    }
  }

  it should "compare both NaR yields ne only" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x8000.U)
      c.io.b.poke(0x8000.U)
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.ne.expect(true.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(false.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(false.B)
    }
  }

  it should "compare positive vs negative" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0x4000.U)  // 1.0
      c.io.b.poke(0xC000.U)  // -1.0
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(false.B)
      c.io.le.expect(false.B)
      c.io.gt.expect(true.B)
      c.io.ge.expect(true.B)
    }
  }

  it should "compare negative vs positive" in {
    test(new PositCMPEndToEnd(p)) { c =>
      c.io.a.poke(0xC000.U)  // -1.0
      c.io.b.poke(0x4000.U)  // 1.0
      c.clock.step(1)
      c.io.eq.expect(false.B)
      c.io.lt.expect(true.B)
      c.io.le.expect(true.B)
      c.io.gt.expect(false.B)
      c.io.ge.expect(false.B)
    }
  }
}
