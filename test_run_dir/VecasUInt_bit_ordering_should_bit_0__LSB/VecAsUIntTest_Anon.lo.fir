FIRRTL version 1.2.0
circuit VecAsUIntTest_Anon :
  module VecAsUIntTest_Anon : @[src/test/scala/gridion/posit/VecTest.scala 11:14]
    input clock : Clock @[src/test/scala/gridion/posit/VecTest.scala 11:14]
    input reset : UInt<1> @[src/test/scala/gridion/posit/VecTest.scala 11:14]
    output io_out : UInt<4> @[src/test/scala/gridion/posit/VecTest.scala 12:18]

    node v_1 = UInt<1>("h0") @[src/test/scala/gridion/posit/VecTest.scala 15:19 17:12]
    node v_0 = UInt<1>("h1") @[src/test/scala/gridion/posit/VecTest.scala 15:19 16:12]
    node io_out_lo = cat(v_1, v_0) @[src/test/scala/gridion/posit/VecTest.scala 20:19]
    node v_3 = UInt<1>("h0") @[src/test/scala/gridion/posit/VecTest.scala 15:19 19:12]
    node v_2 = UInt<1>("h0") @[src/test/scala/gridion/posit/VecTest.scala 15:19 18:12]
    node io_out_hi = cat(v_3, v_2) @[src/test/scala/gridion/posit/VecTest.scala 20:19]
    node _io_out_T = cat(io_out_hi, io_out_lo) @[src/test/scala/gridion/posit/VecTest.scala 20:19]
    io_out <= _io_out_T @[src/test/scala/gridion/posit/VecTest.scala 20:14]
