package gridion.gpu.simt

import chisel3._
import chisel3.util.MuxLookup

object Opcode {
  val NOP      = 0.U(6.W)
  val FADD     = 1.U(6.W)
  val FSUB     = 2.U(6.W)
  val FMUL     = 3.U(6.W)
  val FMA      = 4.U(6.W)
  val FMOV     = 5.U(6.W)

  val FCMP_EQ  = 8.U(6.W)
  val FCMP_NE  = 9.U(6.W)
  val FCMP_LT  = 10.U(6.W)
  val FCMP_LE  = 11.U(6.W)
  val FCMP_GT  = 12.U(6.W)
  val FCMP_GE  = 13.U(6.W)

  val NLOAD    = 14.U(6.W)
  val NADD     = 15.U(6.W)

  val QCLR     = 17.U(6.W)
  val QACC     = 18.U(6.W)
  val QRND     = 19.U(6.W)
  val QLD      = 20.U(6.W)

  val I2F      = 21.U(6.W)
  val F2I      = 22.U(6.W)
  val F2B      = 23.U(6.W)
  val B2F      = 24.U(6.W)

  val IADD     = 25.U(6.W)
  val ISUB     = 26.U(6.W)
  val IMUL     = 27.U(6.W)
  val AND      = 28.U(6.W)
  val OR       = 29.U(6.W)
  val XOR      = 30.U(6.W)
  val SHL      = 31.U(6.W)
  val SHR      = 32.U(6.W)

  val GLOAD    = 33.U(6.W)
  val GSTORE   = 34.U(6.W)
  val SLOAD    = 35.U(6.W)
  val SSTORE   = 36.U(6.W)
  val LDS      = 37.U(6.W)

  val BR       = 40.U(6.W)
  val BRZ      = 41.U(6.W)
  val BRNZ     = 42.U(6.W)
  val CALL     = 43.U(6.W)
  val RET      = 44.U(6.W)
  val BARRIER  = 45.U(6.W)
  val MEMBAR   = 46.U(6.W)

  val SUB_BRD  = 48.U(6.W)
  val SUB_SHUFL = 49.U(6.W)

  def latency(op: UInt): UInt = {
    val lat = Wire(UInt(2.W))
    lat := MuxLookup(op, 1.U(2.W))(Seq(
      FADD  -> 2.U,
      FSUB  -> 2.U,
      FMUL  -> 2.U,
      FMA   -> 3.U,
      QACC  -> 3.U,
      QRND  -> 2.U,
      NADD  -> 3.U,
      GLOAD -> 10.U,
      GSTORE -> 10.U,
      SLOAD -> 5.U,
      SSTORE -> 5.U,
      BR    -> 2.U,
      BRZ   -> 2.U,
      BRNZ  -> 2.U,
      CALL  -> 2.U,
      RET   -> 2.U,
      BARRIER -> 4.U,
    ))
    lat
  }

  def isPositOp(op: UInt): Bool = {
    op === FADD || op === FSUB || op === FMUL || op === FMA || op === FMOV
  }

  def isCmpOp(op: UInt): Bool = {
    op >= FCMP_EQ && op <= FCMP_GE
  }

  def isNbrOp(op: UInt): Bool = {
    op === NLOAD || op === NADD
  }

  def isQuireOp(op: UInt): Bool = {
    op === QCLR || op === QACC || op === QRND || op === QLD
  }

  def isIntOp(op: UInt): Bool = {
    op >= IADD && op <= SHR
  }

  def isMemOp(op: UInt): Bool = {
    op === GLOAD || op === GSTORE || op === SLOAD || op === SSTORE
  }

  def isBrOp(op: UInt): Bool = {
    op === BR || op === BRZ || op === BRNZ || op === CALL || op === RET
  }
}
