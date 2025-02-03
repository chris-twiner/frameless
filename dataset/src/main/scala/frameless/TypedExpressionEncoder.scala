package frameless

import org.apache.spark.sql.catalyst.encoders.AgnosticEncoder
import org.apache.spark.sql.types.StructType

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
    ): AgnosticEncoder[T] = encoder.agnosticEncoder

}
