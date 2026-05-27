package gridion.posit

import chisel3._
import chisel3.util.{PriorityEncoder, log2Ceil}

class PositDecode(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(p.N.W))
    val out = Output(new PositInternal(p))
  })

  val posit = io.in
  val sign = posit(p.N - 1)
  val isZero = posit === 0.U
  val isNaR = posit === (1.U << (p.N - 1))

  val body = Mux(sign,
    ((~posit).asUInt + 1.U)(p.N - 2, 0),
    posit(p.N - 2, 0))

  val msb = body(p.N - 2)

  val mismatch = Wire(Vec(p.bodyWidth - 1, Bool()))
  for (i <- 0 until p.bodyWidth - 1) {
    mismatch(i) := body(p.bodyWidth - 1) =/= body(p.bodyWidth - 2 - i)
  }

  val anyMismatch = mismatch.asUInt.orR
  val firstMismatch = Mux(anyMismatch, PriorityEncoder(mismatch.asUInt), 0.U)
  val k = Mux(anyMismatch, firstMismatch + 1.U, p.bodyWidth.U)

  val kExt = k.zext.asSInt
  val regimeVal = Mux(msb, kExt - 1.S, 0.S - kExt)

  val allBodyRegime = k >= p.bodyWidth.U

  val termPos = Mux(allBodyRegime, 0.U, (p.N - 2).U - k)
  val hasExpFrac = !allBodyRegime && termPos >= p.ES.U

  val expStart = termPos - p.ES.U
  val expBits = Mux(hasExpFrac,
    (body >> expStart)(p.ES - 1, 0),
    0.U(p.ES.W))

  val fracWidth = Mux(hasExpFrac && termPos > p.ES.U,
    termPos - p.ES.U,
    0.U)
  val fracMask = Mux(fracWidth > 0.U, (1.U << fracWidth) - 1.U, 0.U)
  val fracRaw = body & fracMask

  val rawExp = (regimeVal << p.ES) + expBits.zext.asSInt

  val maxFrac = p.maxFracWidth
  val shiftAmt = (maxFrac.U - fracWidth).asUInt
  val alignedFrac = fracRaw << shiftAmt
  val sig = (1.U << maxFrac) | alignedFrac(maxFrac - 1, 0)

  val effExp = rawExp

  val result = Wire(new PositInternal(p))
  result.sign := sign
  result.isZero := isZero
  result.isNaR := isNaR

  when(isZero || isNaR) {
    result.exp := 0.S
    result.sig := 0.U
  }.otherwise {
    result.exp := effExp
    result.sig := sig
  }

  io.out := result
}
