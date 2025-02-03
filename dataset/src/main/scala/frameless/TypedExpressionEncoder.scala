package frameless

import org.apache.spark.sql.Encoder
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoder
import org.apache.spark.sql.types.{DataType, StructType}

object TypedExpressionEncoder {

  /**
   * In Spark, DataFrame has always schema of StructType
   *
   * DataFrames of primitive types become records
   * with a single field called "value" set in ExpressionEncoder.
   */
  def targetStructType[A](encoder: TypedEncoder[A]): StructType = {
    //    org.apache.spark.sql.ShimUtils
    //    .targetStructType(encoder.catalystRepr, encoder.nullable)
    def targetStructType(dataType: DataType, nullable: Boolean): StructType =
      dataType match {
        case x: StructType =>
          if (nullable) StructType(x.fields.map(_.copy(nullable = true)))
          else x

        case dt => new StructType().add("value", dt, nullable = nullable)
      }
    targetStructType(encoder.catalystRepr, encoder.nullable)
  }

  def apply[T](
      implicit
      encoder: TypedEncoder[T]
    ): AgnosticEncoder[T] = encoder.agnosticEncoder

}
