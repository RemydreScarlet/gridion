package gridion.gpu.simt

import chisel3._
import chisel3.util._
import gridion.posit._

class SIMTLane(val p: PositParams = PositParams()) extends Module {
  val io = IO(new Bundle {
    val opcode = Input(UInt(6.W))
    val dst = Input(UInt(4.W))
    val src1 = Input(UInt(4.W))
    val src2 = Input(UInt(4.W))
    val dx = Input(UInt(2.W))
    val dy = Input(UInt(2.W))

    val x = Input(UInt(3.W))
    val y = Input(UInt(3.W))

    val nbrIn = Input(Vec(8, UInt(p.N.W)))
    val nbrOut = Output(UInt(p.N.W))

    val issue = Input(Bool())
    val commit = Input(Bool())

    val memAddr = Output(UInt(32.W))
    val memData = Output(UInt(p.N.W))
    val memReq = Output(Bool())
    val memStore = Output(Bool())
    val memGlobal = Output(Bool())
    val memRespData = Input(UInt(p.N.W))
    val memRespValid = Input(Bool())
  })

  val regFile = Reg(Vec(16, UInt(p.N.W)))
  val src1Val = Wire(UInt(p.N.W))
  val src2Val = Wire(UInt(p.N.W))
  src1Val := regFile(io.src1)
  src2Val := regFile(io.src2)

  io.nbrOut := regFile(io.src1)

  val pipeDst = Reg(UInt(4.W))
  val pipeResult = Reg(UInt(p.N.W))
  val pipeValid = RegInit(false.B)

  val decodeA = Module(new PositDecode(p))
  val decodeB = Module(new PositDecode(p))
  decodeA.io.in := src1Val
  decodeB.io.in := src2Val

  val adder = Module(new PositAdd(p))
  val mul = Module(new PositMul(p))
  val cmp = Module(new PositCMP(p))
  val encode = Module(new PositEncode(p))

  val internalA = decodeA.io.out
  val internalB = decodeB.io.out

  val internalSubB = Wire(new PositInternal(p))
  internalSubB.sign := !internalB.sign
  internalSubB.exp := internalB.exp
  internalSubB.sig := internalB.sig
  internalSubB.isZero := internalB.isZero
  internalSubB.isNaR := internalB.isNaR

  adder.io.a := internalA
  adder.io.b := Mux(io.opcode === Opcode.FSUB, internalSubB, internalB)
  mul.io.a := internalA
  mul.io.b := internalB

  val cmpEq = cmp.io.eq
  val cmpNe = cmp.io.ne
  val cmpLt = cmp.io.lt
  val cmpLe = cmp.io.le
  val cmpGt = cmp.io.gt
  val cmpGe = cmp.io.ge

  val positOne = Wire(UInt(p.N.W))
  positOne := (1.U << (p.N - 2)) | (1.U << (p.N - 2 - 1))

  val positZero = 0.U(p.N.W)

  val cmpResult = MuxLookup(io.opcode, positZero)(Seq(
    Opcode.FCMP_EQ -> Mux(cmpEq, positOne, positZero),
    Opcode.FCMP_NE -> Mux(cmpNe, positOne, positZero),
    Opcode.FCMP_LT -> Mux(cmpLt, positOne, positZero),
    Opcode.FCMP_LE -> Mux(cmpLe, positOne, positZero),
    Opcode.FCMP_GT -> Mux(cmpGt, positOne, positZero),
    Opcode.FCMP_GE -> Mux(cmpGe, positOne, positZero),
  ))

  encode.io.in := Mux(io.opcode === Opcode.FMUL, mul.io.out, adder.io.out)

  val positResult = Mux(io.opcode === Opcode.FMUL, encode.io.out,
                    Mux(io.opcode === Opcode.FSUB || io.opcode === Opcode.FADD, encode.io.out,
                    Mux(io.opcode === Opcode.FMOV, src1Val, cmpResult)))

  val quire = Module(new Quire(p))
  quire.io.clr := io.issue && io.opcode === Opcode.QCLR
  quire.io.acc := io.issue && io.opcode === Opcode.QACC
  quire.io.rnd := io.issue && io.opcode === Opcode.QRND
  quire.io.a := internalA
  quire.io.b := internalB

  val qEncode = Module(new PositEncode(p))
  qEncode.io.in := quire.io.result

  val nbrMuxIdx = MuxLookup(Cat(io.dx, io.dy), 0.U(3.W))(Seq(
    "b00_00".U -> 7.U,
    "b00_01".U -> 6.U,
    "b00_10".U -> 5.U,
    "b01_00".U -> 0.U,
    "b01_10".U -> 4.U,
    "b10_00".U -> 1.U,
    "b10_01".U -> 2.U,
    "b10_10".U -> 3.U,
  ))
  val nbrVal = io.nbrIn(nbrMuxIdx)

  val intResult = MuxLookup(io.opcode, 0.U(p.N.W))(Seq(
    Opcode.IADD -> (src1Val + src2Val),
    Opcode.ISUB -> (src1Val - src2Val),
    Opcode.IMUL -> (src1Val * src2Val)(15, 0),
    Opcode.AND  -> (src1Val & src2Val),
    Opcode.OR   -> (src1Val | src2Val),
    Opcode.XOR  -> (src1Val ^ src2Val),
    Opcode.SHL  -> (src1Val << src2Val(3, 0)),
    Opcode.SHR  -> (src1Val >> src2Val(3, 0)),
  ))

  val isMemOp = Opcode.isMemOp(io.opcode)

  val memAddrRaw = src1Val.zext + src2Val.zext
  io.memAddr := memAddrRaw
  io.memData := regFile(io.dst)
  io.memReq := io.issue && isMemOp
  io.memStore := io.issue && isMemOp && (io.opcode === Opcode.GSTORE || io.opcode === Opcode.SSTORE)
  io.memGlobal := io.issue && isMemOp && (io.opcode === Opcode.GLOAD || io.opcode === Opcode.GSTORE)

  val memDst = Reg(UInt(4.W))
  when(io.issue && isMemOp) {
    memDst := io.dst
  }

  val aluResult = Mux(isMemOp, 0.U(p.N.W),
                  Mux(Opcode.isPositOp(io.opcode) || Opcode.isCmpOp(io.opcode), positResult,
                  Mux(Opcode.isNbrOp(io.opcode), nbrVal,
                  Mux(Opcode.isQuireOp(io.opcode) && io.opcode === Opcode.QRND, qEncode.io.out,
                  Mux(Opcode.isQuireOp(io.opcode), 0.U(p.N.W),
                  Mux(Opcode.isIntOp(io.opcode), intResult, 0.U(p.N.W)))))))

  when(io.memRespValid) {
    regFile(memDst) := io.memRespData
  }

  when(io.issue && io.commit) {
    regFile(io.dst) := aluResult
    pipeValid := false.B
  }.elsewhen(io.issue && !io.commit && !isMemOp) {
    pipeDst := io.dst
    pipeResult := aluResult
    pipeValid := true.B
  }

  when(io.commit && !io.issue && pipeValid) {
    regFile(pipeDst) := pipeResult
    pipeValid := false.B
  }
}
