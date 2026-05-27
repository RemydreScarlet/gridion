package gridion.posit

object SoftPosit {
  val N = 16
  val ES = 1
  val useed = 1 << (1 << ES)  // 4
  val useedBits = 1 << ES  // 2

  def isNaR(p: Int): Boolean = p == 0x8000
  def isZero(p: Int): Boolean = p == 0

  def toFloat(p: Int): Float = {
    if (isZero(p)) return 0.0f
    if (isNaR(p)) return Float.NaN

    val sign = (p >> 15) & 1
    val bits = if (sign == 1) ((~p) & 0xFFFF) + 1 else p
    val body = bits & 0x7FFF

    if (body == 0) return if (sign == 0) 0.0f else -0.0f

    val msb = (body >> 14) & 1
    val leading = if (msb == 1) countLeadingOnes(body, 14) else countLeadingZeros(body, 14)
    val k = leading
    val regimeVal = if (msb == 1) k - 1 else -k

    val termPos = 14 - k
    val hasExpFrac = termPos >= ES

    val expBits = if (hasExpFrac) (body >> (termPos - ES)) & ((1 << ES) - 1) else 0
    val fracWidth = if (hasExpFrac && termPos > ES) termPos - ES else 0
    val fracMask = if (fracWidth > 0) (1 << fracWidth) - 1 else 0
    val fracRaw = body & fracMask

    val pow2 = regimeVal * useedBits + expBits
    val sig = 1.0 + fracRaw.toDouble / (1 << fracWidth)

    val value = sig * Math.pow(2.0, pow2)
    (if (sign == 1) -value else value).toFloat
  }

  def fromFloat(f: Float): Int = {
    if (f.isNaN) return 0x8000
    if (f == 0.0f) return 0

    val sign = if (f < 0) 1 else 0
    val absF = math.abs(f.toDouble)

    var exp = 0
    var sig = absF
    while (sig >= 2.0) { sig /= 2.0; exp += 1 }
    while (sig < 1.0) { sig *= 2.0; exp -= 1 }

    val regimeVal = if (exp >= 0) exp / useedBits else (exp - useedBits + 1) / useedBits
    val expBits = exp - regimeVal * useedBits

    if (regimeVal >= 0) {
      val k = regimeVal + 1
      val regimeLen = k + 1
      if (regimeLen >= N) return if (sign == 1) 0x8001 else 0x7FFF
      var body = 0
      for (i <- 0 until k) body = (body << 1) | 1
      body = (body << 1) | 0
      val remBits = N - 1 - regimeLen
      if (remBits >= ES) {
        body = (body << ES) | (expBits & ((1 << ES) - 1))
        val fracBits = remBits - ES
        val intSig = ((sig - 1.0) * (1 << fracBits)).toInt
        body = (body << fracBits) | (intSig & ((1 << fracBits) - 1))
      } else {
        body = body << remBits
      }
      if (sign == 1) ((-body) & 0xFFFF) else body
    } else {
      val k = -regimeVal
      val regimeLen = k + 1
      if (regimeLen >= N) return if (sign == 1) 0xFFFF else 0x0001
      var body = 0
      for (i <- 0 until k) body = (body << 1) | 0
      body = (body << 1) | 1
      val remBits = N - 1 - regimeLen
      if (remBits >= ES) {
        body = (body << ES) | (expBits & ((1 << ES) - 1))
        val fracBits = remBits - ES
        val intSig = ((sig - 1.0) * (1 << fracBits)).toInt
        body = (body << fracBits) | (intSig & ((1 << fracBits) - 1))
      } else {
        body = body << remBits
      }
      if (sign == 1) ((-body) & 0xFFFF) else body
    }
  }

  def add(a: Int, b: Int): Int = {
    if (isNaR(a) || isNaR(b)) return 0x8000
    if (isZero(a)) return b
    if (isZero(b)) return a
    fromFloat(toFloat(a) + toFloat(b))
  }

  def mul(a: Int, b: Int): Int = {
    if (isNaR(a) || isNaR(b)) return 0x8000
    if (isZero(a) || isZero(b)) return 0
    fromFloat(toFloat(a) * toFloat(b))
  }

  private def countLeadingOnes(x: Int, bitPos: Int): Int = {
    var count = 0
    for (i <- bitPos to 0 by -1) {
      if ((x >> i & 1) == 1) count += 1 else return count
    }
    count
  }

  private def countLeadingZeros(x: Int, bitPos: Int): Int = {
    var count = 0
    for (i <- bitPos to 0 by -1) {
      if ((x >> i & 1) == 0) count += 1 else return count
    }
    count
  }
}
