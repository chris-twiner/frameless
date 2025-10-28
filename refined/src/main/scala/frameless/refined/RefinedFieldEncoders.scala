package frameless.refined

import scala.reflect.ClassTag
import org.apache.spark.sql.types._
import eu.timepit.refined.api.{RefType, Refined, Validate}
import frameless.{RecordFieldEncoder, TypedEncoder}
import org.apache.spark.sql.catalyst.encoders.{AgnosticEncoder, Codec}
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.{EncoderField, OptionEncoder, ProductEncoder, TransformingEncoder}
import shapeless.{Generic, HNil}

private[refined] trait RefinedFieldEncoders {

  /**
   * @tparam T the refined type (e.g. `String`)
   */
  implicit def optionRefined[F[_, _], T, R](
      implicit
      i0: RefType[F],
      i1: TypedEncoder[T],
      i2: ClassTag[F[T, R]]
    ): RecordFieldEncoder[Option[F[T, R]]] =
    new RecordFieldEncoder[Option[F[T, R]]](new TypedEncoder[Option[F[T, R]]] {
      override def nullable: Boolean = true

      // `Refined` is a Value class: https://github.com/fthomas/refined/blob/master/modules/core/shared/src/main/scala-3.0-/eu/timepit/refined/api/Refined.scala#L8
      override def jvmRepr = ObjectType(classOf[Option[F[T, R]]])

      override def agnosticEncoder: AgnosticEncoder[Option[F[T, R]]] =
        OptionEncoder(
          TransformingEncoder[F[T,R], T](
            i2,
            i1.agnosticEncoder,
            () => new Codec[F[T,R], T] {

              override def encode(in: F[T, R]): T = i0.unwrap(in)

              override def decode(out: T): F[T, R] = i0.unsafeWrap(out)
            }
          )
        )

      override def toString = s"optionRefined[${i2.runtimeClass.getName}]"

    })

  /**
   * @tparam T the refined type (e.g. `String`)
   */
  implicit def refined[F[_, _], T, R](
      implicit
      i0: RefType[F],
      i1: TypedEncoder[T],
      i2: ClassTag[F[T, R]]
    ): RecordFieldEncoder[F[T, R]] =
    new RecordFieldEncoder[F[T, R]](new TypedEncoder[F[T, R]] {

      override def agnosticEncoder: AgnosticEncoder[F[T, R]] =
        i1.agnosticEncoder.asInstanceOf[AgnosticEncoder[F[T, R]]]

      override def toString = s"refined[${i2.runtimeClass.getName}]"

    })
}
