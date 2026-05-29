package gridion.posit

import chisel3._

class PositCMP(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val a = Input(new PositInternal(p))
    val b = Input(new PositInternal(p))
    val eq = Output(Bool())
    val ne = Output(Bool())
    val lt = Output(Bool())
    val le = Output(Bool())
    val gt = Output(Bool())
    val ge = Output(Bool())
  })

  val anyNaR = io.a.isNaR || io.b.isNaR

  val aPos = !io.a.sign && !io.a.isZero
  val aNeg = io.a.sign && !io.a.isZero
  val bPos = !io.b.sign && !io.b.isZero
  val bNeg = io.b.sign && !io.b.isZero

  val aZero = io.a.isZero
  val bZero = io.b.isZero

  val bothZero = aZero && bZero

  val aEqB = Wire(Bool())
  val aBeatsB = Wire(Bool())
  when(aPos && bNeg) {
    aBeatsB := true.B
    aEqB := false.B
  }.elsewhen(aNeg && bPos) {
    aBeatsB := false.B
    aEqB := false.B
  }.elsewhen(aZero && bZero) {
    aBeatsB := false.B
    aEqB := true.B
  }.elsewhen(aZero && !bZero) {
    aBeatsB := bNeg
    aEqB := false.B
  }.elsewhen(!aZero && bZero) {
    aBeatsB := aPos
    aEqB := false.B
  }.otherwise {
    val sameSign = io.a.sign
    val expCmp = io.a.exp > io.b.exp
    val expEq = io.a.exp === io.b.exp
    val sigCmp = io.a.sig > io.b.sig
    val sigEq = io.a.sig === io.b.sig
    val magnitudeBeats = expCmp || (expEq && sigCmp)
    aBeatsB := Mux(sameSign, !magnitudeBeats, magnitudeBeats)
    aEqB := expEq && sigEq
  }

  io.eq := !anyNaR && aEqB
  io.ne := anyNaR || !aEqB
  io.lt := !anyNaR && !aEqB && !aBeatsB
  io.le := !anyNaR && (aEqB || !aBeatsB)
  io.gt := !anyNaR && !aEqB && aBeatsB
  io.ge := !anyNaR && (aEqB || aBeatsB)
}
