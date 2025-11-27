package frameless.refined

import scala.reflect.ClassTag
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.types._
import com.sparkutils.shim.expressions.{UnwrapOption2 => UnwrapOption, WrapOption2 => WrapOption}
import org.apache.spark.sql.shim.{Invoke5 => Invoke, NewInstance4 => NewInstance}
import eu.timepit.refined.api.RefType
import frameless.{RecordFieldEncoder, TypedEncoder}
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoder
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.OptionEncoder

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
      new RecordFieldEncoder[Option[F[T, R]]](
      new TypedEncoder[Option[F[T, R]]] {
        override def jvmRepr = ObjectType(classOf[Option[F[T, R]]])
        override def agnosticEncoder: AgnosticEncoder[Option[F[T, R]]] = OptionEncoder(refined.encoder.agnosticEncoder)
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
     new RecordFieldEncoder[F[T, R]](
     new TypedEncoder[F[T, R]] {
       override def jvmRepr: DataType = i1.jvmRepr
       override def agnosticEncoder: AgnosticEncoder[F[T, R]] =
         i1.agnosticEncoder.asInstanceOf[AgnosticEncoder[F[T, R]]]
     })
}
