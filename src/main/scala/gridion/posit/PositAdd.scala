package gridion.posit

import chisel3._
import chisel3.util.{log2Ceil, PriorityEncoder}

class PositAdd(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val a = Input(new PositInternal(p))
    val b = Input(new PositInternal(p))
    val out = Output(new PositInternal(p))
  })

  val out = Wire(new PositInternal(p))
  val special = io.a.isZero || io.a.isNaR || io.b.isZero || io.b.isNaR

  val aIsLarger = io.a.exp >= io.b.exp
  val expDiff = Mux(aIsLarger, (io.a.exp - io.b.exp).asUInt, (io.b.exp - io.a.exp).asUInt)

  val bigSig = Mux(aIsLarger, io.a.sig, io.b.sig)
  val smallSig = Mux(aIsLarger, io.b.sig, io.a.sig)
  val bigExp = Mux(aIsLarger, io.a.exp, io.b.exp)
  val sameSign = io.a.sign === io.b.sign

  val m = p.maxFracWidth
  val sigShift = m + 4
  val sigWidth = m + 1

  val bigExt = bigSig << sigShift
  val maxShift = (sigWidth + sigShift).U
  val smallAligned = Mux(expDiff >= maxShift, 0.U(p.sigWidth + sigShift).asUInt,
    (smallSig << sigShift) >> expDiff)

  val rawSum = Mux(sameSign, bigExt + smallAligned, bigExt - smallAligned)

  val sumNeg = rawSum(rawSum.getWidth - 1)
  val sumAbs = Mux(sumNeg, (~rawSum).asUInt + 1.U, rawSum)

  val searchWidth = sumAbs.getWidth
  val leadingZeros = Wire(UInt(log2Ceil(searchWidth + 1).W))
  val searchVec = Wire(Vec(searchWidth, Bool()))
  for (i <- 0 until searchWidth) {
    searchVec(i) := sumAbs(searchWidth - 1 - i)
  }
  leadingZeros := Mux(sumAbs.orR, PriorityEncoder(searchVec.asUInt), searchWidth.U)

  val normShift = leadingZeros
  val normFull = (sumAbs << normShift)(searchWidth - 1, 0)
  val rawSig = normFull(searchWidth - 1, searchWidth - sigWidth)

  val normExp = bigExp + (searchWidth - 1 - m - sigShift).S - normShift.asSInt

  val zeroResult = !sumAbs.orR

  out.sign := Mux(zeroResult, false.B, io.a.sign ^ sumNeg)
  out.exp := Mux(zeroResult, 0.S, normExp)
  out.sig := Mux(zeroResult, 0.U, rawSig)
  out.isZero := io.a.isZero && io.b.isZero
  out.isNaR := io.a.isNaR || io.b.isNaR

  when(zeroResult && !special) {
    out.sign := false.B
    out.isZero := true.B
  }

  when(special) {
    when(io.a.isNaR || io.b.isNaR) {
      out.sign := false.B
      out.exp := 0.S
      out.sig := 0.U
      out.isZero := false.B
      out.isNaR := true.B
    }.elsewhen(io.a.isZero) {
      out.sign := io.b.sign
      out.exp := io.b.exp
      out.sig := io.b.sig
      out.isZero := io.b.isZero
      out.isNaR := io.b.isNaR
    }.elsewhen(io.b.isZero) {
      out.sign := io.a.sign
      out.exp := io.a.exp
      out.sig := io.a.sig
      out.isZero := io.a.isZero
      out.isNaR := io.a.isNaR
    }
  }

  io.out := out
}
