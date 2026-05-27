package gridion.posit

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class VecAsUIntTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Vec.asUInt bit ordering"

  it should "bit 0 = LSB" in {
    test(new Module {
      val io = IO(new Bundle {
        val out = Output(UInt(4.W))
      })
      val v = Wire(Vec(4, Bool()))
      v(0) := true.B
      v(1) := false.B
      v(2) := false.B
      v(3) := false.B
      io.out := v.asUInt
    }) { c =>
      val result = c.io.out.peekInt()
      println(s"v(0)=1, v(1..3)=0 => 0x${result.toInt.toHexString}")
      // If v(0) is LSB: result = 0x1
      // If v(0) is MSB: result = 0x8
    }
  }
}
