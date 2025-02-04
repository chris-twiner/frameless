package frameless
package ops

import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.TransformingEncoder
import org.apache.spark.sql.catalyst.encoders.{AgnosticEncoder, Codec}
import shapeless.ops.hlist.Align
import shapeless.{::, Generic, HList, HNil, Lazy}

import scala.reflect.ClassTag

/** Evidence for correctness of `TypedDataset[T].as[U]` */
class As[T, U] private (implicit val encoder: TypedEncoder[U])

case class Convertible[A, B, AR <: HList, BR <: HList]()(implicit
   val genA: Generic.Aux[A, AR],
   val genB: Generic.Aux[B, BR],
   val alignEncode: Align[AR, BR],
   val alignDecode: Align[BR, AR],
)

object Convertible {
  def apply[A, B, AR <: HList, BR <: HList](implicit
    convertible: Convertible[A, B, AR, BR]
    ): Convertible[A, B, AR, BR] = convertible

  implicit def derive[A, B, AR <: HList, BR <: HList](implicit
    genA: Generic.Aux[A, AR],
    genB: Generic.Aux[B, BR],
    alignEncode: Align[AR, BR],
    alignDecode: Align[BR, AR],
   ): Convertible[A, B, AR, BR] = new Convertible[A, B, AR, BR]()
}

object As extends LowPriorityAs {

  final class Equiv[A, B] private[ops] (val name: String)

  implicit def equivIdentity[A] = new Equiv[A, A]("identity")

  implicit def deriveAs[A, B, AR <: HList, BR <: HList]
    (implicit
     i0: TypedEncoder[A],
     //i1: Equiv[A, B],
     convertible: Convertible[A, B, AR, BR],
     classTagA: ClassTag[A],
     classTagB: ClassTag[B]
    ): As[A, B] = {
    implicit val encB = new TypedEncoder[B] {
      val provider = () => new Codec[B, A] with Serializable {
        import convertible._
        override def encode(in: B): A = genA.from(alignDecode.apply(genB.to(in)))

        override def decode(out: A): B = genB.from(alignEncode.apply(genA.to(out)))
      }

      override def agnosticEncoder: AgnosticEncoder[B] =
        TransformingEncoder[B, A](
          classTagB,
          i0.agnosticEncoder,
          provider)
    }
    new As[A, B]
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
