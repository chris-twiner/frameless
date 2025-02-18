package frameless

import org.apache.spark.sql.catalyst.encoders.AgnosticEncoder
import org.apache.spark.sql.types.StructType

object TypedExpressionEncoder {

  /**
   * As of Spark 4 TypedExpressionEncoder is simply an alias for AgnosticEncoder
   */
  type TypedExpressionEncoder[A] = AgnosticEncoder[A]

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
    ): TypedExpressionEncoder[T] = encoder.agnosticEncoder
}
