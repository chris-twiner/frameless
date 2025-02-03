package frameless

import java.math.BigInteger
import java.util.Date
import java.time.{Duration, Instant, LocalDate, Period}
import java.sql.Timestamp
import scala.reflect.ClassTag
import FramelessInternals.UserDefinedType
import org.apache.spark.sql.catalyst.expressions.{Expression, Literal, UnsafeArrayData}
import org.apache.spark.sql.catalyst.util.{ArrayBasedMapData, DateTimeUtils, GenericArrayData}
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import shapeless._
import shapeless.ops.hlist.IsHCons
import com.sparkutils.shim.expressions.{ExternalMapToCatalyst7 => ExternalMapToCatalyst, MapObjects5 => MapObjects, UnwrapOption2 => UnwrapOption, WrapOption2 => WrapOption}
import frameless.{reflection => ScalaReflection}
import org.apache.spark.sql.catalyst.encoders.{AgnosticEncoder, Codec}
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.{ArrayEncoder, BinaryEncoder, DEFAULT_JAVA_DECIMAL_ENCODER, DEFAULT_SCALA_DECIMAL_ENCODER, IterableEncoder, JavaBigIntEncoder, MapEncoder, OptionEncoder, PrimitiveBooleanEncoder, PrimitiveByteEncoder, PrimitiveDoubleEncoder, PrimitiveFloatEncoder, PrimitiveIntEncoder, PrimitiveLongEncoder, PrimitiveShortEncoder, STRICT_DATE_ENCODER, STRICT_INSTANT_ENCODER, STRICT_TIMESTAMP_ENCODER, ScalaBigIntEncoder, ScalaDecimalEncoder, StringEncoder, TimestampEncoder, TransformingEncoder, UDTEncoder}
import org.apache.spark.sql.shim.{Invoke5 => Invoke, NewInstance4 => NewInstance, StaticInvoke4 => StaticInvoke}

import java.sql
import scala.collection.immutable.{ListSet, TreeSet}

abstract class TypedEncoder[T](
    implicit
    val classTag: ClassTag[T])
    extends Serializable {
  def nullable: Boolean = false

  def jvmRepr: DataType = agnosticEncoder.dataType
  def catalystRepr: DataType = agnosticEncoder.dataType

  /**
   * Create the underlying AgnosticEncoder
   */
  def agnosticEncoder: AgnosticEncoder[T]
}

object InjectionCodecs {

  def wrap[A, B](injection: Injection[A,B]): () => Codec[A, B] =
    () =>
      new Codec[A, B] {
        override def encode(in: A): B = injection.apply(in)

        override def decode(out: B): A = injection.invert(out)
      }


  val decode: Codec[_, _] => Function[_, _] = (codec: Codec[_, _]) => codec.decode _
  val encode: Codec[_, _] => Function[_, _] = (codec: Codec[_, _]) => codec.encode _

  def convertPossibleValueClass[A, B](recordFieldEncoder: RecordFieldEncoder[_], op: Codec[_,_] => _ => _): A => B = {
    recordFieldEncoder.encoder.agnosticEncoder match {
      case tEnc: TransformingEncoder[_, _] =>
        val dec =
          op(tEnc.
            codecProvider().asInstanceOf[Codec[_, _]])

       recordFieldEncoder.valueClassUnderlying.fold[_ => Any]((a: Any) => a)(_ => {
          a => dec(a)
       }).asInstanceOf[A => B]
      case _ => a => a.asInstanceOf[B]
    }
  }

}

// Waiting on scala 2.12
// @annotation.implicitAmbiguous(msg =
// """TypedEncoder[${T}] can be obtained from automatic type class derivation, using the implicit Injection[${T}, ?] or using the implicit UserDefinedType[${T}] in scope.
// To desambigious this resolution you need to either:
//   - Remove the implicit Injection[${T}, ?] from scope
//   - Remove the implicit UserDefinedType[${T}] from scope
//   - import TypedEncoder.usingInjection
//   - import TypedEncoder.usingDerivation
//   - import TypedEncoder.usingUserDefinedType
// """)
object TypedEncoder {
  def apply[T: TypedEncoder]: TypedEncoder[T] = implicitly[TypedEncoder[T]]

  implicit val stringEncoder: TypedEncoder[String] = new TypedEncoder[String] {
    override def agnosticEncoder: AgnosticEncoder[String] = StringEncoder
    override def jvmRepr: DataType = FramelessInternals.objectTypeFor[String]

    override def toString: String = s"StringEncoder"
  }

  implicit val booleanEncoder: TypedEncoder[Boolean] =
    new TypedEncoder[Boolean] {
      override def agnosticEncoder: AgnosticEncoder[Boolean] = PrimitiveBooleanEncoder
      override def toString: String = s"BooleanEncoder"
    }

  implicit val intEncoder: TypedEncoder[Int] = new TypedEncoder[Int] {
    override def agnosticEncoder: AgnosticEncoder[Int] = PrimitiveIntEncoder
    override def toString: String = s"IntEncoder"
  }

  implicit val longEncoder: TypedEncoder[Long] = new TypedEncoder[Long] {
    override def agnosticEncoder: AgnosticEncoder[Long] = PrimitiveLongEncoder
    override def toString: String = s"LongEncoder"
  }

  implicit val shortEncoder: TypedEncoder[Short] = new TypedEncoder[Short] {
    override def agnosticEncoder: AgnosticEncoder[Short] = PrimitiveShortEncoder
    override def toString: String = s"ShortEncoder"
  }

  implicit val charEncoder: TypedEncoder[Char] = new TypedEncoder[Char] {

    val charAsString: Injection[Char, String] =
      new Injection[Char, String] {
        def apply(a: Char): String = String.valueOf(a)

        def invert(b: String): Char = {
          require(b.length == 1)
          b.charAt(0)
        }
      }

    override def jvmRepr: DataType =
      FramelessInternals.objectTypeFor[java.lang.Character]

    override def agnosticEncoder: AgnosticEncoder[Char] =
      TransformingEncoder[Char, String](
        classTag,
        StringEncoder,
        InjectionCodecs.wrap(charAsString))

    override def toString: String = s"CharEncoder"
  }

  implicit val byteEncoder: TypedEncoder[Byte] = new TypedEncoder[Byte] {
    override def agnosticEncoder: AgnosticEncoder[Byte] = PrimitiveByteEncoder
    override def toString: String = s"ByteEncoder"
  }

  implicit val floatEncoder: TypedEncoder[Float] = new TypedEncoder[Float] {
    override def agnosticEncoder: AgnosticEncoder[Float] = PrimitiveFloatEncoder
    override def toString: String = s"FloatEncoder"
  }

  implicit val doubleEncoder: TypedEncoder[Double] = new TypedEncoder[Double] {
    override def agnosticEncoder: AgnosticEncoder[Double] = PrimitiveDoubleEncoder
    override def toString: String = s"DoubleEncoder"
  }

  implicit val bigDecimalEncoder: TypedEncoder[BigDecimal] =
    new TypedEncoder[BigDecimal] {
      override def jvmRepr: DataType = ScalaReflection.dataTypeFor[BigDecimal]
      override def agnosticEncoder: AgnosticEncoder[BigDecimal] = DEFAULT_SCALA_DECIMAL_ENCODER
      override def toString: String = s"BigDecimalEncoder"
    }

  implicit val javaBigDecimalEncoder: TypedEncoder[java.math.BigDecimal] =
    new TypedEncoder[java.math.BigDecimal] {
      override def jvmRepr: DataType = ScalaReflection.dataTypeFor[java.math.BigDecimal]
      override def agnosticEncoder: AgnosticEncoder[java.math.BigDecimal] = DEFAULT_JAVA_DECIMAL_ENCODER
      override def toString: String = s"JavaBigDecimalEncoder"
    }

  implicit val bigIntEncoder: TypedEncoder[BigInt] = new TypedEncoder[BigInt] {
    override def jvmRepr: DataType = ScalaReflection.dataTypeFor[BigInt]
    override def agnosticEncoder: AgnosticEncoder[BigInt] = ScalaBigIntEncoder
    override def toString: String = s"BigIntEncoder"
  }

  implicit val javaBigIntEncoder: TypedEncoder[BigInteger] =
    new TypedEncoder[BigInteger] {
      override def jvmRepr: DataType = ScalaReflection.dataTypeFor[BigInteger]
      override def agnosticEncoder: AgnosticEncoder[BigInteger] = JavaBigIntEncoder
      override def toString: String = s"JavaBigIntEncoder"
    }

  implicit val sqlDate: TypedEncoder[SQLDate] = new TypedEncoder[SQLDate] {
    // No direct equivalent of invoke <-> staticinvoke pairs but injection works
    override def jvmRepr: DataType = ScalaReflection.dataTypeFor[SQLDate]

    val sqlDateAsDate: Injection[SQLDate, Int] =
      new Injection[SQLDate, Int] {
        def apply(a: SQLDate): Int = a.days

        def invert(b: Int): SQLDate = SQLDate(b)
      }

    override def agnosticEncoder: AgnosticEncoder[SQLDate] =
      TransformingEncoder[SQLDate, Int](
        classTag,
        PrimitiveIntEncoder,
        InjectionCodecs.wrap(sqlDateAsDate))

    override def toString: String = s"SQLDateEncoder"
  }

  implicit val timestampEncoder: TypedEncoder[Timestamp] =
    new TypedEncoder[Timestamp] {
      override def jvmRepr: DataType = ScalaReflection.dataTypeFor[Timestamp]
      override def agnosticEncoder: AgnosticEncoder[Timestamp] = STRICT_TIMESTAMP_ENCODER
      override def toString: String = s"TimeStampEncoder"
    }

  implicit val dateEncoder: TypedEncoder[Date] = new TypedEncoder[Date] {
    // No direct equivalent of invoke <-> staticinvoke pairs but injection works

    override def jvmRepr: DataType = ScalaReflection.dataTypeFor[Date]

    val dateAsInstant: Injection[Date, Instant] =
      new Injection[Date, Instant] {
        def apply(a: Date): Instant = a.toInstant

        def invert(b: Instant): Date = Date.from(b)
      }

    override def agnosticEncoder: AgnosticEncoder[Date] =
      TransformingEncoder[Date, Instant](
        classTag,
        STRICT_INSTANT_ENCODER,
        InjectionCodecs.wrap(dateAsInstant))

    override def toString: String = s"DateEncoder"
  }

  implicit val sqlDateEncoder: TypedEncoder[java.sql.Date] =
    new TypedEncoder[java.sql.Date] {
      override def jvmRepr: DataType = ScalaReflection.dataTypeFor[java.sql.Date]
      override def agnosticEncoder: AgnosticEncoder[sql.Date] = STRICT_DATE_ENCODER
      override def toString: String = s"SQLDateEncoder"
    }

  implicit val sqlTimestamp: TypedEncoder[SQLTimestamp] =
    new TypedEncoder[SQLTimestamp] {
      override def jvmRepr: DataType = ScalaReflection.dataTypeFor[SQLTimestamp]

      val sqlTimestampAsLong: Injection[SQLTimestamp, Long] =
        new Injection[SQLTimestamp, Long] {
          def apply(a: SQLTimestamp): Long = a.us

          def invert(b: Long): SQLTimestamp = SQLTimestamp(b)
        }

      override def agnosticEncoder: AgnosticEncoder[SQLTimestamp] =
        TransformingEncoder[SQLTimestamp, Long](
          classTag,
          PrimitiveLongEncoder,
          InjectionCodecs.wrap(sqlTimestampAsLong))

      override def toString: String = s"SQLTimestampEncoder"
    }

  /** java.time Encoders, Spark uses https://github.com/apache/spark/blob/v3.2.0/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/util/DateTimeUtils.scala for encoding / decoding. */
  implicit val timeInstant: TypedEncoder[Instant] = new TypedEncoder[Instant] {
    override def jvmRepr: DataType = ScalaReflection.dataTypeFor[Instant]
    override def agnosticEncoder: AgnosticEncoder[Instant] = STRICT_INSTANT_ENCODER
    override def toString: String = s"InstantEncoder"
  }

  /**
   * DayTimeIntervalType and YearMonthIntervalType in Spark 3.2.0.
   * We maintain Spark 3.x cross compilation and handle Duration and Period as an injections to be compatible with Spark versions < 3.2
   * See
   *  * https://github.com/apache/spark/blob/v3.2.0/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/util/IntervalUtils.scala#L1031-L1047
   *  * https://github.com/apache/spark/blob/v3.2.0/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/util/IntervalUtils.scala#L1075-L1087
   */
  // DayTimeIntervalType
  implicit val timeDurationInjection: Injection[Duration, Long] =
    Injection(_.toMillis, Duration.ofMillis)

  // YearMonthIntervalType
  implicit val timePeriodInjection: Injection[Period, Int] =
    Injection(_.getDays, Period.ofDays)

  implicit val timePeriodEncoder: TypedEncoder[Period] =
    TypedEncoder.usingInjection

  implicit val timeDurationEncoder: TypedEncoder[Duration] =
    TypedEncoder.usingInjection

  implicit def arrayEncoder[T: ClassTag](
      implicit
      i0: Lazy[RecordFieldEncoder[T]]
    ): TypedEncoder[Array[T]] =
    new TypedEncoder[Array[T]] {
      private lazy val encodeT = i0.value.encoder

      override def jvmRepr: DataType = encodeT.jvmRepr match {
        case ByteType => BinaryType
        case _        => FramelessInternals.objectTypeFor[Array[T]]
      }

      override def agnosticEncoder: AgnosticEncoder[Array[T]] =
        encodeT.jvmRepr match {
          case ByteType => BinaryEncoder.asInstanceOf[AgnosticEncoder[Array[T]]]
          case IntegerType | LongType | DoubleType | FloatType | ShortType |
               BooleanType =>
            ArrayEncoder(encodeT.agnosticEncoder, encodeT.nullable)
          case _ =>
            IterableEncoder(
              classTag,
              encodeT.agnosticEncoder,
              encodeT.nullable,
              lenientSerialization = false).asInstanceOf[AgnosticEncoder[Array[T]]]
            //collectionEncoder(encodeT.agnosticEncoder, containsNull = false)
        }

      override def toString: String = s"ArrayEncoder[$jvmRepr]"
    }

  /**
   * Per #804 - when MapObjects is used in interpreted mode the type returned is Seq, not the derived type used in compilation
   *
   * This type class offers extensible conversion for more specific types.  By default Seq, List and Vector for Seq's and Set, TreeSet and ListSet are supported.
   *
   * @tparam C
   */
  trait CollectionConversion[F[_], C[_], Y] extends Serializable {
    def convert(c: F[Y]): C[Y]
  }

  object CollectionConversion {

    implicit def seqToSeq[Y] = new CollectionConversion[Seq, Seq, Y] {

      override def convert(c: Seq[Y]): Seq[Y] =
        c match {
          // Stream is produced
          case _: Stream[Y] @unchecked => c.toVector.toSeq
          case _                       => c
        }
    }

    implicit def seqToVector[Y] = new CollectionConversion[Seq, Vector, Y] {
      override def convert(c: Seq[Y]): Vector[Y] = c.toVector
    }

    implicit def seqToList[Y] = new CollectionConversion[Seq, List, Y] {
      override def convert(c: Seq[Y]): List[Y] = c.toList
    }

    implicit def setToSet[Y] = new CollectionConversion[Set, Set, Y] {
      override def convert(c: Set[Y]): Set[Y] = c
    }

    implicit def setToTreeSet[Y](
        implicit
        ordering: Ordering[Y]
      ) = new CollectionConversion[Set, TreeSet, Y] {

      override def convert(c: Set[Y]): TreeSet[Y] =
        TreeSet.newBuilder.++=(c).result()
    }

    implicit def setToListSet[Y] = new CollectionConversion[Set, ListSet, Y] {

      override def convert(c: Set[Y]): ListSet[Y] =
        ListSet.newBuilder.++=(c).result()
    }
  }

  implicit def seqEncoder[C[X] <: Seq[X], T](
      implicit
      i0: Lazy[RecordFieldEncoder[T]],
      i1: ClassTag[C[T]],
      i2: CollectionConversion[Seq, C, T]
    ) = collectionEncoder[Seq, C, T]

  implicit def setEncoder[C[X] <: Set[X], T](
      implicit
      i0: Lazy[RecordFieldEncoder[T]],
      i1: ClassTag[C[T]],
      i2: CollectionConversion[Set, C, T]
    ) = collectionEncoder[Set, C, T]

  def collectionEncoder[O[_], C[X], T](
      implicit
      i0: Lazy[RecordFieldEncoder[T]],
      i1: ClassTag[C[T]],
      i2: CollectionConversion[O, C, T]
    ): TypedEncoder[C[T]] = new TypedEncoder[C[T]] {
    private lazy val encodeT = i0.value.encoder

    override def jvmRepr: DataType = FramelessInternals.objectTypeFor[C[T]](i1)
/*
    def catalystRepr: DataType =
      ArrayType(encodeT.catalystRepr, encodeT.nullable)

    def toCatalyst(path: Expression): Expression = {
      val enc = i0.value

      if (ScalaReflection.isNativeType(enc.jvmRepr)) {
        NewInstance(classOf[GenericArrayData], path :: Nil, catalystRepr)
      } else {
        // converts to Seq, both Set and Seq handling must convert to Seq first
        MapObjects(
          enc.toCatalyst,
          SeqCaster(path),
          enc.jvmRepr,
          encodeT.nullable
        )
      }
    }

    def fromCatalyst(path: Expression): Expression =
      CollectionCaster[O, C, T](
        MapObjects(
          i0.value.fromCatalyst,
          path,
          encodeT.catalystRepr,
          encodeT.nullable,
          Some(i1.runtimeClass) // This will cause MapObjects to build a collection of type C[_] directly when compiling
        ),
        implicitly[CollectionConversion[O, C, T]]
      ) // This will convert Seq to the appropriate C[_] when eval'ing.

    override def toString: String = s"collectionEncoder($jvmRepr)" */

    /**
     * Create the underlying AgnosticEncoder
     */
    override def agnosticEncoder: AgnosticEncoder[C[T]] =
      IterableEncoder(
        ClassTag(i1.runtimeClass),
        encodeT.agnosticEncoder,
        encodeT.nullable,
        lenientSerialization = false).asInstanceOf[AgnosticEncoder[C[T]]] // only C is provided

    override def toString: String = s"CollectionEncoder[$jvmRepr]"
  }

  /**
   * @tparam A the key type
   * @tparam B the value type
   * @param i0 the keys encoder
   * @param i1 the values encoder
   */
  implicit def mapEncoder[A: NotCatalystNullable, B](
      implicit
      i0: Lazy[RecordFieldEncoder[A]],
      i1: Lazy[RecordFieldEncoder[B]]
    ): TypedEncoder[Map[A, B]] = new TypedEncoder[Map[A, B]] {
    override def jvmRepr: DataType = FramelessInternals.objectTypeFor[Map[A, B]]

    private lazy val encodeA = i0.value
    private lazy val encodeB = i1.value

    private lazy val convertA: Any => A = InjectionCodecs.convertPossibleValueClass(encodeA, InjectionCodecs.decode)
    private lazy val convertB: Any => B = InjectionCodecs.convertPossibleValueClass(encodeB, InjectionCodecs.decode)

    private lazy val revertA: Any => A = InjectionCodecs.convertPossibleValueClass(encodeA, InjectionCodecs.encode)
    private lazy val revertB: Any => B = InjectionCodecs.convertPossibleValueClass(encodeB, InjectionCodecs.encode)

    val provider = () => new Codec[Map[A,B], Map[_,_]] {

      override def decode(in: Map[_, _]): Map[A, B] = in.map { p =>
        (convertA(p._1), convertB(p._2))
      }

      override def encode(out: Map[A, B]): Map[_, _] = out.map { p =>
        (revertA(p._1), revertB(p._2))
      }
    }

    // MAP key / values with TransformingEncoder as top level do not seem to work
    override def agnosticEncoder: AgnosticEncoder[Map[A, B]] = {
      TransformingEncoder[Map[A,B],Map[_,_]](
        classTag,
        MapEncoder(
          classTag.asInstanceOf[ClassTag[Map[_,_]]],
          encodeA.valueClassUnderlying.fold[AgnosticEncoder[A]](encodeA.encoder.agnosticEncoder)(_.agnosticEncoder.asInstanceOf[AgnosticEncoder[A]]),
          encodeB.valueClassUnderlying.fold[AgnosticEncoder[B]](encodeB.encoder.agnosticEncoder)(_.agnosticEncoder.asInstanceOf[AgnosticEncoder[B]]),
          valueContainsNull = false),
        provider
      )
    }
    /*
        lazy val catalystRepr: DataType =
          MapType(encodeA.catalystRepr, encodeB.catalystRepr, encodeB.nullable)

        def fromCatalyst(path: Expression): Expression = {
          val keyArrayType = ArrayType(encodeA.catalystRepr, containsNull = false)

          val keyData = Invoke(
            MapObjects(
              i0.value.fromCatalyst,
              Invoke(path, "keyArray", keyArrayType),
              encodeA.catalystRepr
            ),
            "array",
            FramelessInternals.objectTypeFor[Array[Any]]
          )

          val valueArrayType = ArrayType(encodeB.catalystRepr, encodeB.nullable)

          val valueData = Invoke(
            MapObjects(
              i1.value.fromCatalyst,
              Invoke(path, "valueArray", valueArrayType),
              encodeB.catalystRepr
            ),
            "array",
            FramelessInternals.objectTypeFor[Array[Any]]
          )

          StaticInvoke(
            ArrayBasedMapData.getClass,
            jvmRepr,
            "toScalaMap",
            keyData :: valueData :: Nil
          )
        }

        def toCatalyst(path: Expression): Expression = {
          val encA = i0.value
          val encB = i1.value

          ExternalMapToCatalyst(
            path,
            encA.jvmRepr,
            encA.toCatalyst,
            false,
            encB.jvmRepr,
            encB.toCatalyst,
            encodeB.nullable
          )
        }

        override def toString = s"mapEncoder($jvmRepr)"*/

    override def toString: String = s"MapEncoder[$jvmRepr]"
  }

  implicit def optionEncoder[A](
      implicit
      underlying: TypedEncoder[A]
    ): TypedEncoder[Option[A]] =
    new TypedEncoder[Option[A]] {

      override def nullable: Boolean = true

      override def jvmRepr: DataType =
        FramelessInternals.objectTypeFor[Option[A]](classTag)
/*
      def catalystRepr: DataType = underlying.catalystRepr

      def toCatalyst(path: Expression): Expression = {
        // for primitive types we must manually unbox the value of the object
        underlying.jvmRepr match {
          case IntegerType =>
            Invoke(
              UnwrapOption(
                ScalaReflection.dataTypeFor[java.lang.Integer],
                path
              ),
              "intValue",
              IntegerType
            )

          case LongType =>
            Invoke(
              UnwrapOption(ScalaReflection.dataTypeFor[java.lang.Long], path),
              "longValue",
              LongType
            )

          case DoubleType =>
            Invoke(
              UnwrapOption(ScalaReflection.dataTypeFor[java.lang.Double], path),
              "doubleValue",
              DoubleType
            )

          case FloatType =>
            Invoke(
              UnwrapOption(ScalaReflection.dataTypeFor[java.lang.Float], path),
              "floatValue",
              FloatType
            )

          case ShortType =>
            Invoke(
              UnwrapOption(ScalaReflection.dataTypeFor[java.lang.Short], path),
              "shortValue",
              ShortType
            )

          case ByteType =>
            Invoke(
              UnwrapOption(ScalaReflection.dataTypeFor[java.lang.Byte], path),
              "byteValue",
              ByteType
            )

          case BooleanType =>
            Invoke(
              UnwrapOption(
                ScalaReflection.dataTypeFor[java.lang.Boolean],
                path
              ),
              "booleanValue",
              BooleanType
            )

          case _ =>
            underlying.toCatalyst(UnwrapOption(underlying.jvmRepr, path))
        }
      }

      def fromCatalyst(path: Expression): Expression =
        WrapOption(underlying.fromCatalyst(path), underlying.jvmRepr)*/

      /**
       * Create the underlying AgnosticEncoder
       */
      override def agnosticEncoder: AgnosticEncoder[Option[A]] = OptionEncoder(underlying.agnosticEncoder)

      override def toString: String = s"OptionEncoder[$jvmRepr]"
    }

  /** Encodes things using injection if there is one defined */
  implicit def usingInjection[A: ClassTag, B](
      implicit
      inj: Injection[A, B],
      trb: TypedEncoder[B]
    ): TypedEncoder[A] =
    new TypedEncoder[A] {
      override def jvmRepr: DataType = FramelessInternals.objectTypeFor[A](classTag)

      override def agnosticEncoder: AgnosticEncoder[A] =
        TransformingEncoder[A, B](
          classTag,
          trb.agnosticEncoder,
          InjectionCodecs.wrap(inj))

      override def toString: String = s"InjectionEncoder"
    }

  /** Encodes things as records if there is no Injection defined */
  implicit def usingDerivation[F, G <: HList, H <: HList](
      implicit
      i0: LabelledGeneric.Aux[F, G],
      i1: DropUnitValues.Aux[G, H],
      i2: IsHCons[H],
      i3: Lazy[RecordEncoderFields[H]],
      i4: Lazy[NewInstanceExprs[G]],
      i5: ClassTag[F]
    ): TypedEncoder[F] = new RecordEncoder[F, G, H]

  /** Encodes things using a Spark SQL's User Defined Type (UDT) if there is one defined in implicit */
  implicit def usingUserDefinedType[
      A >: Null: UserDefinedType: ClassTag
    ]: TypedEncoder[A] = {
    val udt = implicitly[UserDefinedType[A]]

    new TypedEncoder[A] {
      override def jvmRepr: DataType = ObjectType(udt.userClass)

      /**
       * Create the underlying AgnosticEncoder
       */
      override def agnosticEncoder: AgnosticEncoder[A] =
        UDTEncoder[A](udt, udt.getClass)

      override def toString: String = s"UserDefinedTypeEncoder"
    }
  }

  object injections extends InjectionEnum
}
