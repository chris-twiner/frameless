package frameless

import com.sparkutils.shim.deriveUnitLiteral
import org.apache.spark.sql.catalyst.encoders.{ AgnosticEncoder, Codec }
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.{
  EncoderField,
  ProductEncoder,
  TransformingEncoder
}
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.types._
import shapeless._
import shapeless.labelled.FieldType
import shapeless.ops.hlist.IsHCons
import shapeless.ops.record.Keys

import scala.reflect.ClassTag

class RecordFieldEncoder[T](
    val encoder: TypedEncoder[T],
    val valueClassUnderlying: Option[TypedEncoder[_]] = None)
    extends Serializable

case class RecordEncoderField(
    ordinal: Int,
    name: String,
    encoder: TypedEncoder[_],
    valueClassUnderlying: Option[TypedEncoder[_]] = None)

trait RecordEncoderFields[T <: HList] extends Serializable {
  def value: List[RecordEncoderField]

  override def toString: String =
    s"""RecordEncoderFields${value.mkString("[", ", ", "]")}"""
}

object RecordEncoderFields {

  implicit def deriveRecordLast[K <: Symbol, H](
      implicit
      key: Witness.Aux[K],
      head: RecordFieldEncoder[H]
    ): RecordEncoderFields[FieldType[K, H] :: HNil] =
    new RecordEncoderFields[FieldType[K, H] :: HNil] {
      def value: List[RecordEncoderField] = fieldEncoder[K, H] :: Nil
    }

  implicit def deriveRecordCons[K <: Symbol, H, T <: HList](
      implicit
      key: Witness.Aux[K],
      head: RecordFieldEncoder[H],
      tail: RecordEncoderFields[T]
    ): RecordEncoderFields[FieldType[K, H] :: T] =
    new RecordEncoderFields[FieldType[K, H] :: T] {

      def value: List[RecordEncoderField] =
        fieldEncoder[K, H] :: tail.value.map(x =>
          x.copy(ordinal = x.ordinal + 1)
        )
    }

  private def fieldEncoder[K <: Symbol, H](
      implicit
      key: Witness.Aux[K],
      e: RecordFieldEncoder[H]
    ): RecordEncoderField =
    RecordEncoderField(0, key.value.name, e.encoder, e.valueClassUnderlying)
}

/**
 * Drops fields with Unit type from labelled generic representation of types.
 *
 * @tparam L labelled generic representation of type fields
 */
trait DropUnitValues[L <: HList] extends DepFn1[L] with Serializable {
  type Out <: HList
}

object DropUnitValues {

  def apply[L <: HList](
      implicit
      dropUnitValues: DropUnitValues[L]
    ): Aux[L, dropUnitValues.Out] = dropUnitValues

  type Aux[L <: HList, Out0 <: HList] = DropUnitValues[L] { type Out = Out0 }

  implicit def deriveHNil[H]: Aux[HNil, HNil] = new DropUnitValues[HNil] {
    type Out = HNil
    def apply(l: HNil): Out = HNil
  }

  implicit def deriveUnit[K <: Symbol, T <: HList, OutT <: HList](
      implicit
      dropUnitValues: DropUnitValues.Aux[T, OutT]
    ): Aux[FieldType[K, Unit] :: T, OutT] =
    new DropUnitValues[FieldType[K, Unit] :: T] {
      type Out = OutT
      def apply(l: FieldType[K, Unit] :: T): Out = dropUnitValues(l.tail)
    }

  implicit def deriveNonUnit[K <: Symbol, V, T <: HList, OutH, OutT <: HList](
      implicit
      nonUnit: V =:!= Unit,
      dropUnitValues: DropUnitValues.Aux[T, OutT]
    ): Aux[FieldType[K, V] :: T, FieldType[K, V] :: OutT] =
    new DropUnitValues[FieldType[K, V] :: T] {
      type Out = FieldType[K, V] :: OutT
      def apply(l: FieldType[K, V] :: T): Out = l.head :: dropUnitValues(l.tail)
    }
}

class RecordEncoder[F, G <: HList, H <: HList](
    implicit
    i0: LabelledGeneric.Aux[F, G],
    i1: DropUnitValues.Aux[G, H],
    i2: IsHCons[H],
    fields: Lazy[RecordEncoderFields[H]],
    classTag: ClassTag[F])
    extends TypedEncoder[F] {

  override def agnosticEncoder: AgnosticEncoder[F] =
    ProductEncoder[F](
      classTag,
      fields.value.value.map(f =>
        EncoderField(
          f.name,
          f.valueClassUnderlying.fold[AgnosticEncoder[_]](
            f.encoder.agnosticEncoder
          )(_.agnosticEncoder),
          f.encoder.nullable,
          Metadata.empty
        )
      ),
      None
    )

  override def jvmRepr: DataType = FramelessInternals.objectTypeFor[F]

  override def toString: String = s"RecordEncoder[$jvmRepr]"

}

object RecordFieldEncoder extends RecordFieldEncoderLowPriority {

  /**
   * @tparam F the value class
   * @tparam G the single field of the value class
   * @tparam H the single field of the value class (with guarantee it's not a `Unit` value)
   * @tparam K the key type for the fields
   * @tparam V the inner value type
   */
  implicit def optionValueClass[
      F: IsValueClass,
      G <: ::[_, HNil],
      H <: ::[_ <: FieldType[_ <: Symbol, _], HNil],
      K <: Symbol,
      V,
      KS <: ::[_ <: Symbol, HNil]
    ](implicit
      i0: LabelledGeneric.Aux[F, G],
      i1: DropUnitValues.Aux[G, H],
      i2: IsHCons.Aux[H, _ <: FieldType[K, V], HNil],
      i3: Keys.Aux[H, KS],
      i4: IsHCons.Aux[KS, K, HNil],
      i5: TypedEncoder[V],
      i6: ClassTag[F],
      i8: ClassTag[V],
      i9: Generic.Aux[F, V :: HNil]
    ): RecordFieldEncoder[Option[F]] = {
    new RecordFieldEncoder(TypedEncoder.optionEncoder(valueClass.encoder))

  }

  /**
   * The labeled type implicits are needed to differentiate field types, i9 is the actual usage for the Codec
   * @tparam F the value class
   * @tparam G the single field of the value class
   * @tparam H the single field of the value class (with guarantee it's not a `Unit` value)
   * @tparam V the inner value type
   */
  implicit def valueClass[
      F: IsValueClass,
      G <: ::[_, HNil],
      H <: ::[_ <: FieldType[_ <: Symbol, _], HNil],
      K <: Symbol,
      V,
      KS <: ::[_ <: Symbol, HNil]
    ](implicit
      i0: LabelledGeneric.Aux[F, G],
      i1: DropUnitValues.Aux[G, H],
      i2: IsHCons.Aux[H, _ <: FieldType[K, V], HNil],
      i3: Keys.Aux[H, KS],
      i4: IsHCons.Aux[KS, K, HNil],
      i5: TypedEncoder[V],
      i6: ClassTag[F],
      i8: ClassTag[V],
      i9: Generic.Aux[F, V :: HNil]
    ): RecordFieldEncoder[F] = new RecordFieldEncoder(
    new TypedEncoder[F]() {
      override def nullable: Boolean = i5.nullable

      override def agnosticEncoder: AgnosticEncoder[F] = {

        TransformingEncoder[F, V](
          i6,
          i5.agnosticEncoder,
          () =>
            new Codec[F, V] {
              override def encode(in: F): V = Generic[F].to(in).head

              override def decode(out: V): F = i9.from(out :: HNil)
            }
        )
      }

      override def jvmRepr: DataType = FramelessInternals.objectTypeFor[V](i8)

      override def toString: String =
        s"ValueClassEncoder[${i6.runtimeClass.getName} wraps $jvmRepr]"
    },
    Some(i5)
  )
}

private[frameless] sealed trait RecordFieldEncoderLowPriority {

  implicit def apply[T](
      implicit
      e: TypedEncoder[T]
    ): RecordFieldEncoder[T] =
    new RecordFieldEncoder[T](e)
}
