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

  val aBeatsB = Wire(Bool())
  when(aPos && bNeg) {
    aBeatsB := true.B
  }.elsewhen(aNeg && bPos) {
    aBeatsB := false.B
  }.elsewhen(aZero && bZero) {
    aBeatsB := false.B
  }.elsewhen(aZero && !bZero) {
    aBeatsB := bNeg
  }.elsewhen(!aZero && bZero) {
    aBeatsB := aPos
  }.otherwise {
    val sameSign = io.a.sign
    val expCmp = io.a.exp > io.b.exp
    val expEq = io.a.exp === io.b.exp
    val sigCmp = io.a.sig > io.b.sig
    val magnitudeBeats = expCmp || (expEq && sigCmp)
    aBeatsB := Mux(sameSign, !magnitudeBeats, magnitudeBeats)
  }

  io.eq := !anyNaR && bothZero
  io.ne := anyNaR || !bothZero
  io.lt := !anyNaR && !bothZero && !aBeatsB
  io.le := !anyNaR && (bothZero || !aBeatsB)
  io.gt := !anyNaR && !bothZero && aBeatsB
  io.ge := !anyNaR && (bothZero || aBeatsB)
}
