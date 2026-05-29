package gridion.gpu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import gridion.posit._
import gridion.gpu.simt.{SIMTLane, Opcode}

class SIMTLaneTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = PositParams()

  def preloadReg(c: SIMTLane, addr: Int, value: Int): Unit = {
    c.io.test.get.wrEn.poke(true.B)
    c.io.test.get.wrAddr.poke(addr.U)
    c.io.test.get.wrData.poke(value.U)
    c.clock.step(1)
    c.io.test.get.wrEn.poke(false.B)
  }

  def readReg(c: SIMTLane, addr: Int): BigInt = {
    c.io.test.get.rdAddr.poke(addr.U)
    c.clock.step(1)
    c.io.test.get.rdData.peekInt()
  }

  behavior of "SIMTLane"

  it should "execute FMOV: copy register" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 0x4000)

      c.io.opcode.poke(Opcode.FMOV)
      c.io.dst.poke(2.U)
      c.io.src1.poke(1.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 2)
      assert(result == 0x4000, s"FMOV: $result != 0x4000")
    }
  }

  it should "execute FADD: 1.0 + 2.0 = 3.0" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      val a = SoftPosit.fromFloat(1.0f)
      val b = SoftPosit.fromFloat(2.0f)
      val expected = SoftPosit.fromFloat(3.0f)
      preloadReg(c, 1, a)
      preloadReg(c, 2, b)

      c.io.opcode.poke(Opcode.FADD)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3).toInt
      val resF = SoftPosit.toFloat(result)
      val refF = SoftPosit.toFloat(expected)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"FADD 1+2: $result ($resF) vs $expected ($refF)")
    }
  }

  it should "execute FSUB: 3.0 - 1.0 = 2.0" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      val a = SoftPosit.fromFloat(3.0f)
      val b = SoftPosit.fromFloat(1.0f)
      val expected = SoftPosit.fromFloat(2.0f)
      preloadReg(c, 1, a)
      preloadReg(c, 2, b)

      c.io.opcode.poke(Opcode.FSUB)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3).toInt
      val resF = SoftPosit.toFloat(result)
      val refF = SoftPosit.toFloat(expected)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"FSUB 3-1: $result ($resF) vs $expected ($refF)")
    }
  }

  it should "execute FMUL: 1.5 * 2.0 = 3.0" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      val a = SoftPosit.fromFloat(1.5f)
      val b = SoftPosit.fromFloat(2.0f)
      val expected = SoftPosit.fromFloat(3.0f)
      preloadReg(c, 1, a)
      preloadReg(c, 2, b)

      c.io.opcode.poke(Opcode.FMUL)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3).toInt
      val resF = SoftPosit.toFloat(result)
      val refF = SoftPosit.toFloat(expected)
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.01f, s"FMUL 1.5*2: $result ($resF) vs $expected ($refF)")
    }
  }

  it should "execute FCMP_EQ: 1.0 == 1.0 -> true" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 0x4000)
      preloadReg(c, 2, 0x4000)

      c.io.opcode.poke(Opcode.FCMP_EQ)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3)
      assert(result != 0, s"FCMP_EQ: got 0 (false), expected non-zero (true)")
    }
  }

  it should "execute FCMP_GT: 3.0 > 1.0 -> true" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, SoftPosit.fromFloat(3.0f))
      preloadReg(c, 2, SoftPosit.fromFloat(1.0f))

      c.io.opcode.poke(Opcode.FCMP_GT)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3)
      assert(result != 0, s"FCMP_GT 3>1: got 0 (false), expected non-zero (true)")
    }
  }

  it should "execute NLOAD from neighbor" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      c.io.nbrIn(0).poke(0x5000.U)  // neighbor at offset (1,-1) = port 0 = 2.0

      c.io.opcode.poke(Opcode.NLOAD)
      c.io.dst.poke(3.U)
      c.io.dx.poke(1.U)
      c.io.dy.poke(0.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3)
      assert(result == 0x5000, s"NLOAD: $result != 0x5000")
    }
  }

  it should "execute IADD: 3 + 5 = 8" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 3)
      preloadReg(c, 2, 5)

      c.io.opcode.poke(Opcode.IADD)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3)
      assert(result == 8, s"IADD 3+5: $result != 8")
    }
  }

  it should "execute AND: 0x0F & 0x33 = 0x03" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 0x0F)
      preloadReg(c, 2, 0x33)

      c.io.opcode.poke(Opcode.AND)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3)
      assert(result == 0x03, s"AND: $result != 0x03")
    }
  }

  it should "execute OR: 0x0F | 0x30 = 0x3F" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 0x0F)
      preloadReg(c, 2, 0x30)

      c.io.opcode.poke(Opcode.OR)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3)
      assert(result == 0x3F, s"OR: $result != 0x3F")
    }
  }

  it should "execute SHL: 0x01 << 3 = 0x08" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 0x01)
      preloadReg(c, 2, 3)

      c.io.opcode.poke(Opcode.SHL)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3)
      assert(result == 0x08, s"SHL: $result != 0x08")
    }
  }

  it should "execute QACC then QRND: accumulate 1*2 -> round to 2.0" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      val a = SoftPosit.fromFloat(1.0f)
      val b = SoftPosit.fromFloat(2.0f)
      preloadReg(c, 1, a)
      preloadReg(c, 2, b)

      // QCLR
      c.io.opcode.poke(Opcode.QCLR)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)

      // QACC
      c.io.opcode.poke(Opcode.QACC)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)

      // QRND -> dst = 3
      c.io.opcode.poke(Opcode.QRND)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(true.B)
      c.clock.step(1)
      c.io.issue.poke(false.B)
      c.io.commit.poke(false.B)

      val result = readReg(c, 3).toInt
      val resF = SoftPosit.toFloat(result)
      val refF = SoftPosit.toFloat(SoftPosit.fromFloat(2.0f))
      val err = math.abs(refF - resF) / math.max(math.abs(refF), 1e-10f)
      assert(err < 0.05f, s"Quire 1*2: $result ($resF) vs 2.0 ($refF)")
    }
  }

  it should "emit memory request for GLOAD" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 0x1000)
      preloadReg(c, 2, 0x0020)

      c.io.opcode.poke(Opcode.GLOAD)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(false.B)

      assert(c.io.memReq.peekBoolean(), "memReq should be true for GLOAD")
      assert(c.io.memGlobal.peekBoolean(), "memGlobal should be true for GLOAD")
      assert(!c.io.memStore.peekBoolean(), "memStore should be false for GLOAD")

      c.clock.step(1)
      c.io.issue.poke(false.B)
    }
  }

  it should "emit memory request for SSTORE" in {
    test(new SIMTLane(p, addTestHarness = true)) { c =>
      c.io.x.poke(0.U)
      c.io.y.poke(0.U)
      preloadReg(c, 1, 0x0100)
      preloadReg(c, 2, 0x0020)
      preloadReg(c, 3, 0x4000)

      c.io.opcode.poke(Opcode.SSTORE)
      c.io.dst.poke(3.U)
      c.io.src1.poke(1.U)
      c.io.src2.poke(2.U)
      c.io.issue.poke(true.B)
      c.io.commit.poke(false.B)

      assert(c.io.memReq.peekBoolean(), "memReq should be true for SSTORE")
      assert(c.io.memStore.peekBoolean(), "memStore should be true for SSTORE")
      assert(!c.io.memGlobal.peekBoolean(), "memGlobal should be false for SSTORE")

      c.clock.step(1)
      c.io.issue.poke(false.B)
    }
  }
}
