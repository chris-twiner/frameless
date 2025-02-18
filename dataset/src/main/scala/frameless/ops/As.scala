package frameless
package ops

import org.apache.spark.sql.{ Column, Dataset }
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.{
  EncoderField,
  ProductEncoder,
  RowEncoder
}
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoder
import shapeless.{ ::, Generic, HList, Lazy }

import scala.reflect.ClassTag

/** Evidence for correctness of `TypedDataset[T].as[U]` */
class As[T, U] private (
    val resultingEncoder: TypedEncoder[U],
    val originalEncoder: TypedEncoder[T]) {

  /**
   * Without a projection the types may convert properly but the column names will reflect the previous products
   * The encoders are traversed across fields
   *
   * @return
   */
  def asExpressions(dataset: Dataset[T]): Seq[Column] = {
    def mapFields(
        fromFields: Seq[EncoderField],
        toFields: Seq[EncoderField]
      ) = {
      fromFields.zip(toFields).map {
        case (
              EncoderField(fromName, fromEnc, _, _, _, _),
              EncoderField(toName, toEnc, _, _, _, _)
            ) =>
          dataset.col(fromName).as(toName).cast(toEnc.dataType)
      }
    }

    (
      originalEncoder.agnosticEncoder: AgnosticEncoder[_],
      resultingEncoder.agnosticEncoder: AgnosticEncoder[_]
    ) match {
      case (ProductEncoder(_, fromFields, _), ProductEncoder(_, toFields, _)) =>
        mapFields(fromFields, toFields)

      case (RowEncoder(fromFields), ProductEncoder(_, toFields, _)) =>
        mapFields(fromFields, toFields)

      case (ProductEncoder(_, fromFields, _), RowEncoder(toFields)) =>
        mapFields(fromFields, toFields)

      case _ =>
        ???
    }
  }

}

object As extends LowPriorityAs {

  final class Equiv[A, B] private[ops] (val name: String)

  implicit def equivIdentity[A] = new Equiv[A, A]("identity")

  implicit def deriveAs[A, B](
      implicit
      i0: TypedEncoder[A],
      i1: TypedEncoder[B],
      i2: Lazy[Equiv[A, B]]
    ): As[A, B] = new As[A, B](i1, i0)
}

trait LowPriorityAs {

  import As.Equiv

  implicit def equivHList[AH, AT <: HList, BH, BT <: HList](
      implicit
      i0: Lazy[Equiv[AH, BH]],
      i1: Equiv[AT, BT]
    ): Equiv[AH :: AT, BH :: BT] = new Equiv[AH :: AT, BH :: BT]("hlist")

  implicit def equivGeneric[A, B, R, S](
      implicit
      i0: Generic.Aux[A, R],
      i1: Generic.Aux[B, S],
      i2: Lazy[Equiv[R, S]]
    ): Equiv[A, B] = new Equiv[A, B]("generic")
}
