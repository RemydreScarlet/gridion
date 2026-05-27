package gridion.posit

import chisel3._
import chisel3.util.{Cat, log2Ceil}

class PositEncode(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new PositInternal(p))
    val out = Output(UInt(p.N.W))
  })

  val u = io.in
  val maxFrac = p.maxFracWidth

  val fracNorm = u.sig(maxFrac - 1, 0)
  val combinedExp = u.exp

  val rawRegime = combinedExp >> p.ES
  val rawExpBits = combinedExp(p.ES - 1, 0)

  val clampMax = (p.N - 2).S
  val clampMin = (-(p.N - 1)).S

  val regimeClipped = Wire(SInt(p.maxExpBits.W))
  when(rawRegime > clampMax) {
    regimeClipped := clampMax
  }.elsewhen(rawRegime < clampMin) {
    regimeClipped := clampMin
  }.otherwise {
    regimeClipped := rawRegime
  }

  val regimePos = regimeClipped >= 0.S
  val k = Mux(regimePos, (regimeClipped + 1.S).asUInt, (-regimeClipped).asUInt)

  val termPos = (p.bodyWidth - 1).U - k
  val remBits = termPos

  val overflow = k > (p.bodyWidth - 1).U

  val expField = Mux(!overflow && remBits >= p.ES.U, rawExpBits, 0.U(p.ES.W))
  val maxFracU = maxFrac.U

  val fracField = Mux(!overflow && remBits > p.ES.U,
    fracNorm >> ((maxFracU - (remBits - p.ES.U)).asUInt),
    0.U(maxFrac.W))

  val bodyBits = Wire(Vec(p.bodyWidth, Bool()))

  for (pos <- 0 until p.bodyWidth) {
    val posU = pos.U

    val hasFrac = !overflow && termPos > p.ES.U
    val isRegime = !overflow && posU >= termPos
    val isExp = !overflow && posU < termPos && posU >= (termPos - p.ES.U)
    val isFrac = hasFrac && posU < (termPos - p.ES.U)

    val regimeVal = Wire(Bool())
    regimeVal := Mux(overflow, Mux(regimePos, true.B, false.B), false.B)
    when(isRegime) {
      when(posU === termPos) {
        regimeVal := Mux(regimePos, false.B, true.B)
      }.otherwise {
        regimeVal := Mux(regimePos, true.B, false.B)
      }
    }

    val expVal = Wire(Bool())
    expVal := false.B
    when(isExp) {
      val expOff = termPos - posU - 1.U
      expVal := expField(expOff)
    }

    val fracVal = Wire(Bool())
    fracVal := false.B
    when(isFrac) {
      val fracOff = termPos - p.ES.U - posU - 1.U
      val fracWidthU = remBits - p.ES.U
      val fracIdx = fracWidthU - 1.U - fracOff
      fracVal := fracField(fracIdx)
    }

    bodyBits(pos) := regimeVal | expVal | fracVal
  }

  val bodyWire = bodyBits.asUInt
  val fullPosit = Cat(0.U(1.W), bodyWire).asUInt

  val result = Wire(UInt(p.N.W))
  when(u.isZero) {
    result := 0.U
  }.elsewhen(u.isNaR) {
    result := (1.U << (p.N - 1))
  }.elsewhen(u.sign) {
    result := (~fullPosit).asUInt + 1.U
  }.otherwise {
    result := fullPosit
  }

  io.out := result
}
