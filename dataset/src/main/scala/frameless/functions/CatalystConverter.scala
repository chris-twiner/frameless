package frameless.functions

import frameless.{ TypedEncoder, TypedExpressionEncoder }
import org.apache.spark.sql.catalyst.{ CatalystTypeConverters, InternalRow }
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import org.apache.spark.sql.catalyst.expressions.codegen.{
  CodeGenerator,
  CodegenContext
}
import org.apache.spark.sql.types.DataType

trait CatalystConverter[T] {
  def dataType: DataType
  def responseToCatalyst: TypedEncoder[T]

  def responseExprEnc: ExpressionEncoder[T]

  lazy val isSerializedAsStructForTopLevel =
    responseExprEnc.isSerializedAsStructForTopLevel

  def fromCatalyst(
      catalyst: Any,
      expressionEncoder: ExpressionEncoder[Any]
    ): Any = {
    if (expressionEncoder.isSerializedAsStructForTopLevel)
      expressionEncoder
        .createDeserializer()
        .apply(catalyst.asInstanceOf[InternalRow])
    else
      expressionEncoder.createDeserializer().apply(InternalRow(catalyst))
  }

  def processResponse(jvm: T): Any = {

    val returnCatalyst = responseExprEnc.createSerializer().apply(jvm)
    val retval =
      if (returnCatalyst == null)
        null
      else if (isSerializedAsStructForTopLevel)
        returnCatalyst
      else
        returnCatalyst.get(0, dataType)

    retval
  }

  def responseCatalystConverter: Any => Any = {
    val toRow = responseExprEnc.createSerializer().asInstanceOf[Any => Any]
    if (isSerializedAsStructForTopLevel) { value: Any =>
      if (value == null) null else toRow(value).asInstanceOf[InternalRow]
    } else { value: Any =>
      if (value == null) null
      else toRow(value).asInstanceOf[InternalRow].get(0, dataType)
    }
  }

  // must be called before += this
  def responseConversionTerm(ctx: CodegenContext): String = {
    val retConverter = responseCatalystConverter
    val retConverterTerm = ctx.addReferenceObj(
      "retConverter",
      retConverter,
      classOf[Any => Any].getName
    )
    retConverterTerm
  }

  def nullable: Boolean
  // invocation logic taken from Spark4 ScalaUDF

  def responseInvocation(
      ctx: CodegenContext,
      actualFuncCall: String,
      retConverterTerm: String
    ): (String, String) = {
    val internalTpe = CodeGenerator.boxedType(responseToCatalyst.jvmRepr)
    val internalTerm =
      ctx.addMutableState(internalTpe, ctx.freshName("internal"))

    // invocation logic taken from Spark4 ScalaUDF
    val funcInvocation =
      if (
        responseToCatalyst.agnosticEncoder.isPrimitive
        // If the output is nullable, the returned value must be unwrapped from the Option
        && !nullable
      ) {
        s"$internalTerm = ($internalTpe)$actualFuncCall;"
      } else {
        s"""$internalTerm = ($internalTpe)$retConverterTerm.apply(
           $actualFuncCall
        );"""
      }

    (funcInvocation, internalTerm)
  }
}
