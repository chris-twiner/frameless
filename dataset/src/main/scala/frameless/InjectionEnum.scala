package frameless

import frameless.InjectionCodecs.codec
import org.apache.spark.sql.catalyst.encoders.Codec
import shapeless._

import scala.reflect.ClassTag

trait InjectionEnum {
  object CodecEnums {
    /**
     * names are kept to shadow
     */

    implicit val cnilInjectionEnum: TypedInjection[CNil, String] =
      TypedInjection[CNil, String](
        // $COVERAGE-OFF$No value of type CNil so impossible to test
        _ => throw new Exception("Impossible"),
        // $COVERAGE-ON$
        name =>
          throw new IllegalArgumentException(
            s"Cannot construct a value of type CNil: $name did not match data constructor names"
          )
      )

    implicit def coproductInjectionEnum[H, T <: Coproduct](
        implicit
        typeable: Typeable[H],
        gen: Generic.Aux[H, HNil],
        tInjectionEnum: TypedInjection[T, String]
      ): TypedInjection[H :+: T, String] = {
      val dataConstructorName = typeable.describe.takeWhile(_ != '.')

      val underlying = tInjectionEnum.codecProvider()

      TypedInjection(
        {
          case Inl(_) => dataConstructorName
          case Inr(t) => underlying.encode(t)
        },
        { name =>
          if (name == dataConstructorName)
            Inl(gen.from(HNil))
          else
            Inr(underlying.decode(name))
        }
      )
    }

    implicit def genericInjectionEnum[A: ClassTag, R](
        implicit
        gen: Generic.Aux[A, R],
        rInjectionEnum: TypedInjection[R, String]
      ): TypedInjection[A, String] = {
      val underlying = rInjectionEnum.codecProvider()
      TypedInjection[A, String](
        value => underlying.encode(gen.to(value)),
        name => gen.from(underlying.decode(name))
      )
    }
  }

  implicit val cnilInjectionEnum: Injection[CNil, String] =
    Injection(
      // $COVERAGE-OFF$No value of type CNil so impossible to test
      _ => throw new Exception("Impossible"),
      // $COVERAGE-ON$
      name =>
        throw new IllegalArgumentException(
          s"Cannot construct a value of type CNil: $name did not match data constructor names"
        )
    )

  implicit def coproductInjectionEnum[H, T <: Coproduct](
      implicit
      typeable: Typeable[H] ,
      gen: Generic.Aux[H, HNil],
      tInjectionEnum: Injection[T, String]
    ): Injection[H :+: T, String] = {
    val dataConstructorName = typeable.describe.takeWhile(_ != '.')

    Injection(
      {
        case Inl(_) => dataConstructorName
        case Inr(t) => tInjectionEnum.apply(t)
      },
      { name =>
        if (name == dataConstructorName)
          Inl(gen.from(HNil))
        else
          Inr(tInjectionEnum.invert(name))
      }
    )
  }

  implicit def genericInjectionEnum[A, R](
      implicit
      gen: Generic.Aux[A, R],
      rInjectionEnum: Injection[R, String]
    ): Injection[A, String] =
    Injection(
      value => rInjectionEnum(gen.to(value)),
      name => gen.from(rInjectionEnum.invert(name))
    )
}
