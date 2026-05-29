package gridion.gpu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import gridion.gpu.simt.Opcode

class ComputeUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  val NOP = 0
  val IADD = 25
  val GLOAD = 33
  val GSTORE = 34

  def encode(opcode: Int, dst: Int, src1: Int, src2: Int): Int = {
    (opcode << 10) | (dst << 7) | (src1 << 4) | src2
  }

  def writeMicrocode(c: ComputeUnit, addr: Int, data: Int): Unit = {
    c.io.microcodeWrEn.poke(true.B)
    c.io.microcodeWrAddr.poke(addr.U)
    c.io.microcodeData.poke(data.U)
    c.clock.step(1)
    c.io.microcodeWrEn.poke(false.B)
  }

  def preloadReg(c: ComputeUnit, reg: Int, value: Int): Unit = {
    c.io.test.get.lane.wrEn.poke(true.B)
    c.io.test.get.lane.wrAddr.poke(reg.U)
    c.io.test.get.lane.wrData.poke(value.U)
    c.clock.step(1)
    c.io.test.get.lane.wrEn.poke(false.B)
  }

  def readReg(c: ComputeUnit, reg: Int): BigInt = {
    c.io.test.get.lane.rdAddr.poke(reg.U)
    c.clock.step(1)
    c.io.test.get.lane.rdData.peekInt()
  }

  behavior of "ComputeUnit"

  it should "load microcode and execute IADD with preloaded registers" in {
    test(new ComputeUnit(addTestHarness = true, numLanes = 2)) { c =>
      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      preloadReg(c, 1, 10)
      preloadReg(c, 2, 20)

      val r1a = readReg(c, 1)
      assert(r1a == 10, s"preload R1 = $r1a")

      writeMicrocode(c, 0, 0x0000)                                     // NOP (fetch bubble)
      writeMicrocode(c, 1, encode(IADD, 3, 1, 2))                      // IADD R3,R1,R2
      writeMicrocode(c, 2, 0x0000)                                     // NOP padding

      c.io.start.poke(true.B)
      c.io.kernelAddr.poke(1.U)
      c.clock.step(1)
      c.io.start.poke(false.B)

      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      c.clock.step(5)

      val r3 = readReg(c, 3)
      val r1b = readReg(c, 1)
      val r2b = readReg(c, 2)
      assert(r1b == 10, s"R1 after: $r1b, expected 10")
      assert(r2b == 20, s"R2 after: $r2b, expected 20")
      assert(r3 == 30, s"IADD: R3 = $r3, expected 30")
    }
  }

  it should "execute GLOAD and deliver data to lane register" in {
    test(new ComputeUnit(addTestHarness = true, numLanes = 2)) { c =>
      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      writeMicrocode(c, 0, 0x0000)                                     // NOP (fetch bubble)
      writeMicrocode(c, 1, encode(GLOAD, 1, 0, 0))                     // GLOAD R1,[R0+R0]
      writeMicrocode(c, 2, 0x0000)                                     // NOP padding

      c.io.start.poke(true.B)
      c.io.kernelAddr.poke(1.U)
      c.clock.step(1)
      c.io.start.poke(false.B)

      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      var memSeen = false
      for (_ <- 0 until 50) {
        c.clock.step(1)
        if (c.io.memReq.peekBoolean() && !memSeen) {
          memSeen = true
          c.io.memRespData.poke(0xABCD.U)
          c.io.memRespValid.poke(true.B)
        }
      }

      assert(memSeen, "GLOAD memory request should have been issued")

      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      c.clock.step(10)

      val r1 = readReg(c, 1)
      assert(r1 == 0xABCD, s"GLOAD: R1 = 0x${r1.toString(16)}, expected 0xABCD")
    }
  }

  it should "execute GSTORE and emit memory write" in {
    test(new ComputeUnit(addTestHarness = true, numLanes = 4)) { c =>
      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      preloadReg(c, 1, 0xDEAD)

      writeMicrocode(c, 0, 0x0000)                                     // NOP (fetch bubble)
      writeMicrocode(c, 1, encode(GSTORE, 1, 0, 0))                    // GSTORE R1,[R0+R0]
      writeMicrocode(c, 2, 0x0000)                                     // NOP padding

      c.io.start.poke(true.B)
      c.io.kernelAddr.poke(1.U)
      c.clock.step(1)
      c.io.start.poke(false.B)

      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      var storeSeen = false
      var storeAddr = BigInt(0)
      var storeData = BigInt(0)
      for (_ <- 0 until 50) {
        c.clock.step(1)
        if (c.io.memReq.peekBoolean() && c.io.memWr.peekBoolean() && !storeSeen) {
          storeSeen = true
          storeAddr = c.io.memAddr.peekInt()
          storeData = c.io.memWrData.peekInt()
        }
      }

      assert(storeSeen, "GSTORE memory request should have been issued")
      assert(storeAddr == 0, s"GSTORE addr: 0x${storeAddr.toString(16)}, expected 0x0")
      assert(storeData == 0xDEAD, s"GSTORE data: 0x${storeData.toString(16)}, expected 0xDEAD")
    }
  }

  it should "execute multi-instruction program: GLOAD + IADD + GSTORE" in {
    test(new ComputeUnit(addTestHarness = true, numLanes = 4)) { c =>
      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      writeMicrocode(c, 0, 0x0000)                                     // NOP (fetch bubble)
      writeMicrocode(c, 1, encode(GLOAD, 1, 0, 0))                     // GLOAD R1,[R0+R0]
      writeMicrocode(c, 2, 0x0000)                                     // NOP (padding — skipped by stall)
      writeMicrocode(c, 3, encode(IADD, 2, 1, 1))                      // IADD R2,R1,R1 (R2 = 2*R1)
      writeMicrocode(c, 4, encode(GSTORE, 2, 0, 0))                    // GSTORE R2,[R0+R0]

      c.io.start.poke(true.B)
      c.io.kernelAddr.poke(1.U)
      c.clock.step(1)
      c.io.start.poke(false.B)

      c.io.memRespData.poke(0.U)
      c.io.memRespValid.poke(false.B)

      var loadProvided = false
      var storeSeen = false
      var storeData = BigInt(0)
      for (_ <- 0 until 100) {
        c.clock.step(1)
        val memReq = c.io.memReq.peekBoolean()
        val memWr = c.io.memWr.peekBoolean()

        if (memReq && !memWr && !loadProvided) {
          c.io.memRespData.poke(0x1234.U)
          c.io.memRespValid.poke(true.B)
          loadProvided = true
        }

        if (memReq && memWr && loadProvided && !storeSeen) {
          storeSeen = true
          storeData = c.io.memWrData.peekInt()
        }
      }

      assert(loadProvided, "GLOAD should have been issued")
      assert(storeSeen, "GSTORE should have been issued")
      assert(storeData == 0x2468, s"GSTORE data: 0x${storeData.toString(16)}, expected 0x2468 (2*0x1234)")
    }
  }
}
