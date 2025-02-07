package frameless

import shapeless.{Generic, HList, Lazy, Poly1}
import shapeless.ops.hlist.{LiftAll, Mapper}

import java.time.{Duration, Instant, Period}
import scala.annotation.implicitNotFound

/** Types that can be provided with zero's by Catalyst - no zero's were hurt during this
 *  Used by min/first/ etc. to provide generalised coalesce, the zero value is used to
 *  represent null handling only and should not be received, if it is desired then you
 *  must break free of TypedDataset's guardrails.
 */
@implicitNotFound("Cannot provide zero value columns of type ${A}.")
abstract class CatalystZero[A](implicit ev: NotCatalystNullable[A]) {
  def zero: A
}

object CatalystZero {
  def apply[A: NotCatalystNullable](zero: A): CatalystZero[A] = {
    val _zero = zero
    new CatalystZero[A] { val zero: A = _zero }
  }

  implicit val framelessZeroLong      : CatalystZero[Long]       = CatalystZero(zero = 0L)
  implicit val framelessZeroBigDecimal: CatalystZero[BigDecimal] = CatalystZero(zero = BigDecimal(0))
  implicit val framelessZeroDouble    : CatalystZero[Double]     = CatalystZero(zero = 0.0)
  implicit val framelessZeroInt       : CatalystZero[Int]        = CatalystZero(zero = 0)
  implicit val framelessZeroShort     : CatalystZero[Short]      = CatalystZero(zero = 0)
  implicit val framelessZeroBooleanOrdered     : CatalystZero[Boolean]      = CatalystZero(zero = false)
  implicit val framelessZeroByte        : CatalystZero[Byte]         = CatalystZero(zero = 0)
  implicit val framelessZeroFloat       : CatalystZero[Float]        = CatalystZero(zero = 0.0f)
  implicit val framelessZeroSQLDate     : CatalystZero[SQLDate]      = CatalystZero(zero = SQLDate(0))
  implicit val framelessZeroSQLTimestamp: CatalystZero[SQLTimestamp] = CatalystZero(zero = SQLTimestamp(0))
  implicit val framelessZeroString      : CatalystZero[String]       = CatalystZero(zero = "")
  implicit val framelessZeroInstant     : CatalystZero[Instant]      = CatalystZero(zero = Instant.now())
  implicit val framelessZeroDuration    : CatalystZero[Duration]     = CatalystZero(zero = Duration.ZERO)
  implicit val framelessZeroPeriod      : CatalystZero[Period]       = CatalystZero(zero = Period.ZERO)

  object ZeroPoly extends Poly1 {

    implicit def caseZero[U: CatalystZero] =
      at[CatalystZero[U]](c => c.zero)

  }

  implicit def deriveGeneric[G, H <: HList, O <: HList]
    (implicit
     i0: Generic.Aux[G, H],
     i1: Lazy[LiftAll.Aux[CatalystZero, H, O]],
     i2: Mapper.Aux[ZeroPoly.type, O, H],
    ): CatalystZero[G] = CatalystZero(i0.from(i1.value.instances.map(ZeroPoly)))
}
