package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class VecTest2 extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Vec.asUInt with when"

  it should "static assignment" in {
    test(new Module {
      val io = IO(new Bundle {
        val out = Output(UInt(15.W))
      })
      val v = Wire(Vec(15, Bool()))
      v.foreach(_ := false.B)
      v(14) := true.B
      io.out := v.asUInt
    }) { c =>
      val r = c.io.out.peekInt()
      println(s"v(14)=1, rest=0 => 0x${r.toInt.toHexString}")
    }
  }

  it should "with multiple when blocks" in {
    test(new Module {
      val io = IO(new Bundle {
        val out = Output(UInt(15.W))
      })
      val v = Wire(Vec(15, Bool()))
      v.foreach(_ := false.B)
      val cond = Wire(Bool())
      cond := true.B
      for (i <- 0 until 15) {
        val pos = 14 - i
        when(cond) {
          when(pos.U === 13.U) {
            v(pos) := false.B
          }.otherwise {
            v(pos) := true.B // all bits = 1 except bit 13
          }
        }
      }
      io.out := v.asUInt
    }) { c =>
      val r = c.io.out.peekInt()
      println(s"all 1s except bit 13 => 0x${r.toInt.toHexString}")
    }
  }
}
