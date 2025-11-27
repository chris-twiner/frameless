package frameless

import org.apache.spark.sql.catalyst.encoders.{AgnosticEncoder, Codec, JavaSerializationCodec, KryoSerializationCodecImpl}
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.{EncoderField, ProductEncoder, TransformingEncoder}
import org.apache.spark.sql.types.{Metadata, StructType}

import scala.reflect.ClassTag

object TypedExpressionEncoder {

  /**
   * In Spark, DataFrame has always schema of StructType
   *
   * DataFrames of primitive types become records
   * with a single field called "value" set in ExpressionEncoder.
   */
  def targetStructType[A](encoder: TypedEncoder[A]): StructType =
    org.apache.spark.sql.ShimUtils
      .targetStructType(encoder.catalystRepr, encoder.nullable)

  def apply[T](
      implicit
      encoder: TypedEncoder[T]
    ): AgnosticEncoder[T] = {

    import encoder.classTag
/*
    // spark special cases option as a top return value
    // it cannot cascade this through agnostic encoders up from nested encoders
    // a simple way to verify if we have a need for option is if the top encoder is itself nullable
    // An injection of type [Int, I[Option[Int]]] will not be a struct
    if (encoder.nullable && encoder.catalystRepr.isInstanceOf[StructType]) {
      TransformingEncoder(
        implicitly[ClassTag[T]],
        ProductEncoder(
          implicitly[ClassTag[SparkValueClass[T]]],
          Seq(EncoderField("a", encoder.agnosticEncoder, nullable = true, Metadata.empty)),
          None),
        codecProvider = () => new Codec[T, SparkValueClass[T]] {
          override def encode(in: T): SparkValueClass[T] = SparkValueClass(in)
          override def decode(out: SparkValueClass[T]): T = out.a
        }
      )
    } else */
      encoder.agnosticEncoder
  }

}

private case class SparkValueClass[A](a: A)