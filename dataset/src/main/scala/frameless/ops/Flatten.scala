package frameless.ops
import shapeless.ops.hlist.{Drop, Length, Take}
import shapeless.{::, Generic, HList, HNil, Lazy, Nat, syntax}
import syntax.std.tuple._

/*
 inspired by https://github.com/hammerlab/shapeless-utils/blob/master/shared/src/main/scala/org/hammerlab/shapeless/hlist/Flatten.scala
 adds reverse function as well
 */

import shapeless.ops.hlist.Prepend

/**
 * Type-class that computes a recursively-flattened [[HList]] `F` for an input type [[In]].
 *
 * Case-classes and [[HList]]s are expanded.
 */
trait Flatten[In]
  extends Serializable {
  type Out <: HList
  def apply(l: In): Out
  def reverse(l: Out): In
}

trait LowestPri {
  type Aux[In, Out0] = Flatten[In] { type Out = Out0 }

  def make[In, Out0 <: HList](fn: In ⇒ Out0, refn: Out0 => In): Aux[In, Out0] =
    new Flatten[In] {
      type Out = Out0
      override def apply(l: In) = fn(l)

      override def reverse(l: Out0): In = refn(l)
    }
}

trait LowPriFlattenedImplicits extends LowestPri {
  // prepend an element directly if it can't be flattened further (via higher-priority implicits below)
  implicit def directCons[
    H,
    T <: HList,
    FT <: HList
  ](
     implicit
     ft: Lazy[Aux[T, FT]]
   ):
  Aux[
    H ::  T,
    H :: FT
  ] =
    make( {
      case h :: t ⇒
        h :: ft.value(t)
    }, {
      case h :: t =>
        h :: ft.value.reverse(t)
    })
}

object Flatten extends LowPriFlattenedImplicits {

  def apply[In](implicit flat: Flatten[In]): Aux[In, flat.Out] = flat

  implicit val hnil: Aux[HNil, HNil] = make({l ⇒ l},{l ⇒ l})

  // Flatten and prepend a Product (e.g. case-class)
  implicit def nestedCCCons[
    H <: Product,
    FH <: HList,
    T <: HList,
    FT <: HList,
    Out <: HList,
    LEN <: Nat
  ](
     implicit
     fh : Lazy[Aux[H, FH]],
     ft : Lazy[Aux[T, FT]],
     ++ : Prepend.Aux[FH, FT, Out],
     lenFH: Length.Aux[FH, LEN],
     actualInt: shapeless.ops.nat.ToInt[LEN],
     take: Take[Out, LEN#N],
     drop: Drop[Out, LEN#N]
   ): Aux[H :: T, Out] =
    make( {
      case h :: t ⇒
        ++(
          fh.value(h),
          ft.value(t)
        )
    }, {
      case list: Out ⇒
       // val list
        //rvp.a(list)
        val size = actualInt
        val hlist = take(list)
        val rlist = drop(list)
        val h = fh.value.reverse(hlist.asInstanceOf[FH])
        val rest = ft.value.reverse(rlist.asInstanceOf[FT])
        h :: rest
    })

  // Flatten a case-class directly
  implicit def cc[
    CC <: Product,
    L <: HList,
    FL <: HList
  ](
     implicit
     gen: Generic.Aux[CC, L],
     flat: Lazy[Aux[L, FL]]
   ): Aux[CC, FL] =
    make({
      cc ⇒
        flat.value(
          gen.to(cc)
        )
    },{
      cc ⇒
        gen.from(
          flat.value.reverse(cc)
        )
    })

  implicit class Ops[T](val t: T) extends AnyVal {
    def flattenIt(implicit f: Flatten[T]): f.Out = f(t)
  }
}
