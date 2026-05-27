package gridion.posit

import chisel3._
import chisel3.util.log2Ceil

case class PositParams(N: Int = 16, ES: Int = 1) {
  val useed: Int = 1 << (1 << ES)
  val useedBits: Int = 1 << ES
  val maxExpBits: Int = log2Ceil(N) + ES + 1

  val sigWidth: Int = 24
  val bodyWidth: Int = N - 1
  val maxFracWidth: Int = N - 3 - ES
}

class PositInternal(val p: PositParams) extends Bundle {
  val sign = Bool()
  val exp = SInt(p.maxExpBits.W)
  val sig = UInt(p.sigWidth.W)
  val isZero = Bool()
  val isNaR = Bool()
}
