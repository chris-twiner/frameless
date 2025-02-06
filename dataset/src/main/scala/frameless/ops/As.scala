package frameless
package ops

import frameless.ops.As.Equiv
import frameless.ops.Flatten.Aux
import org.apache.spark.sql.{Column, Dataset}
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.{EncoderField, ProductEncoder, RowEncoder, TransformingEncoder}
import org.apache.spark.sql.catalyst.encoders.{AgnosticEncoder, Codec}
import org.apache.spark.sql.types.Metadata
import shapeless.ops.hlist.Align
import shapeless.{::, Generic, HList, HNil, LabelledGeneric, Lazy}

import scala.reflect.ClassTag

/** Evidence for correctness of `TypedDataset[T].as[U]` */
class As[T, U] private (val transformingEncoder: TypedEncoder[U], val resultingEncoder: TypedEncoder[U], val originalEncoder: TypedEncoder[T]) {

  /**
   * Without a projection the types may convert properly but the column names will reflect the previous products
   * The encoders are traversed across fields
   *
   * @return
   */
  def asExpressions(dataset: Dataset[T]): Seq[Column] = {
    def mapFields(fromFields: Seq[EncoderField], toFields: Seq[EncoderField]) = {
      fromFields.zip(toFields).map {
        case (EncoderField(fromName, fromEnc, _, _, _, _), EncoderField(toName, toEnc, _, _, _, _)) =>
          dataset.col(fromName).as(toName).cast(toEnc.dataType)
      }
    }

    (originalEncoder.agnosticEncoder: AgnosticEncoder[_], resultingEncoder.agnosticEncoder: AgnosticEncoder[_]) match {
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

trait Convertible[A, B] extends Codec[A,B] {
  type Intermediate <: HList
  implicit val flattenA: Flatten.Aux[A, Intermediate]
  implicit val flattenB: Flatten.Aux[B, Intermediate]

  override def encode(in: A): B = flattenB.reverse(flattenA.apply(in))

  override def decode(out: B): A = flattenA.reverse(flattenB.apply(out))
}

object Convertible {
  type Aux[A, B, HList] = Convertible[A, B] { type Intermediate = HList }

  implicit def derive[A, B, IntermediateI <: HList](implicit
                                                   evidenceOfEquivalence: Equiv[A, B],
                                                   iflattenA: Flatten.Aux[A, IntermediateI],
                                                   iflattenB: Flatten.Aux[B, IntermediateI]
                                                  ): Convertible[A, B] = new Convertible[A, B] {
    override type Intermediate = IntermediateI
    override implicit val flattenA: _root_.frameless.ops.Flatten.Aux[A, IntermediateI] = iflattenA
    override implicit val flattenB: _root_.frameless.ops.Flatten.Aux[B, IntermediateI] = iflattenB
  }

  def apply[A, B](implicit convertible: Convertible[A,B]): Aux[A, B, convertible.Intermediate] = convertible
}

object As extends LowPriorityAs {

  final class Equiv[A, B] private[ops] (val name: String)

  implicit def equivIdentity[A] = new Equiv[A, A]("identity")

  implicit def deriveAs[A, B]
    (implicit
     i0: TypedEncoder[A],
     i1: TypedEncoder[B],
     convertible: Convertible[A, B],
     classTagB: ClassTag[B]
    ): As[A, B] = {
    val encB = new TypedEncoder[B] {
      val provider = () => new Codec[B, A] with Serializable {

        override def encode(in: B): A = convertible.decode(in)

        override def decode(out: A): B = convertible.encode(out)
      }

      override def agnosticEncoder: AgnosticEncoder[B] =
        TransformingEncoder[B, A](
          classTagB,
          i0.agnosticEncoder,
          provider)
    }
    new As[A, B](encB, i1, i0)
  }

}

trait LowPriorityAs {

  import As.Equiv

  implicit def equivHList[AH, AT <: HList, BH, BT <: HList]
    (implicit
      i0: Lazy[Equiv[AH, BH]],
      i1: Equiv[AT, BT]
    ): Equiv[AH :: AT, BH :: BT] = new Equiv[AH :: AT, BH :: BT]("hlist")

  implicit def equivGeneric[A, B, R, S]
    (implicit
      i0: Generic.Aux[A, R],
      i1: Generic.Aux[B, S],
      i2: Lazy[Equiv[R, S]]
    ): Equiv[A, B] = new Equiv[A, B]("generic")
}
