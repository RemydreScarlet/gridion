package gridion.posit

import chisel3._

class PositMul(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val a = Input(new PositInternal(p))
    val b = Input(new PositInternal(p))
    val out = Output(new PositInternal(p))
  })

  val result = Wire(new PositInternal(p))

  val special = io.a.isZero || io.a.isNaR || io.b.isZero || io.b.isNaR

  val sign = io.a.sign ^ io.b.sign
  val expSum = io.a.exp + io.b.exp
  val sigProd = io.a.sig * io.b.sig

  val m = p.maxFracWidth
  val prodMSB = sigProd(2 * m + 1)  // bit 25 (0-indexed in 48-bit product)
  val prodShift = Mux(prodMSB, (m + 1).U, m.U)

  val normSig = (sigProd >> prodShift)(m, 0)
  val normExp = expSum + Mux(prodMSB, 1.S, 0.S)

  val isZeroOut = io.a.isZero || io.b.isZero
  val isNaROut = io.a.isNaR || io.b.isNaR

  result.sign := Mux(special, Mux(isNaROut, false.B, false.B), sign)
  result.exp := Mux(special, 0.S, normExp)
  result.sig := Mux(special, 0.U, normSig)
  result.isZero := Mux(isNaROut, false.B, isZeroOut)
  result.isNaR := isNaROut

  io.out := result
}
