package gridion.posit

import chisel3._
import chisel3.util.{log2Ceil, PriorityEncoder}

class Quire(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val clr = Input(Bool())
    val acc = Input(Bool())
    val rnd = Input(Bool())
    val a = Input(new PositInternal(p))
    val b = Input(new PositInternal(p))
    val result = Output(new PositInternal(p))
    val valid = Output(Bool())
  })

  val quire = RegInit(0.S(128.W))

  val sigProd = io.a.sig * io.b.sig
  val expSum = io.a.exp + io.b.exp

  val productSigned = Mux(io.a.sign ^ io.b.sign,
    -(sigProd.zext),
    sigProd.zext)

  val expBias = (64 - 2 * p.maxFracWidth).S
  val shiftAmt = expSum + expBias
  val shiftPos = shiftAmt >= 0.S
  val absShift = Mux(shiftPos, shiftAmt.asUInt, (-shiftAmt).asUInt)

  val prod128 = Wire(SInt(128.W))
  prod128 := productSigned.asSInt

  val shifted = Wire(SInt(128.W))
  when(shiftPos) {
    shifted := (prod128 << absShift(6, 0))(127, 0).asSInt
  }.otherwise {
    shifted := (prod128 >> absShift(6, 0))(127, 0).asSInt
  }

  when(io.clr) {
    quire := 0.S
  }.elsewhen(io.acc) {
    quire := quire +& shifted
  }

  val absQuire = Mux(quire < 0.S, (-quire).asUInt, quire.asUInt)
  val quireHigh = absQuire(127, 64)
  val quireLow = absQuire(63, 0)

  val highNonZero = quireHigh.orR
  val searchVec = Wire(UInt(128.W))
  searchVec := absQuire

  val leadingZeroVec = Wire(Vec(128, Bool()))
  for (i <- 0 until 128) {
    leadingZeroVec(i) := absQuire(127 - i)
  }
  val leadingZeros = Mux(absQuire.orR,
    PriorityEncoder(leadingZeroVec.asUInt),
    128.U)

  val leadingBit = (127.U - leadingZeros).asUInt
  val underflow = leadingZeros >= 128.U
  val rndExp = Mux(underflow, 0.S, leadingBit.zext.asSInt - 64.S)
  val normShift = Mux(underflow, 0.U, leadingBit - p.maxFracWidth.U)

  val rawSig = Mux(underflow,
    0.U(p.sigWidth.W),
    (absQuire >> normShift)(p.sigWidth - 1, 0))

  val result = Wire(new PositInternal(p))
  result.sign := Mux(underflow, false.B, quire < 0.S)
  result.exp := rndExp
  result.sig := rawSig
  result.isZero := underflow || absQuire === 0.U
  result.isNaR := false.B

  io.result := result
  io.valid := io.rnd
}
