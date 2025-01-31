package frameless

/**
 * Taken from https://gist.github.com/SystemFw/7995fd708898e1be51901c7af38853be
 */

import shapeless._, labelled._, ops.record.Selector

trait Accessors[T, I] {
  type Out
  def value: Out
}
object Accessors {
  type Aux[T, I, O] = Accessors[T, I] { type Out = O }

  class Curry[T] {
    def get[L](implicit ev: LabelledGeneric.Aux[T, L],
               a: Accessors[T, L]): a.Out =
      a.value
  }

  def of[T] = new Curry[T]

  def instance[T, I, O](body: O): Accessors.Aux[T, I, O] =
    new Accessors[T, I] {
      type Out = O
      def value = body
    }

  implicit def hnil[T]: Accessors.Aux[T, HNil, HNil] = instance(HNil)

  implicit def kv[T, K <: Symbol, V, R <: HList, R1 <: HList](
                                                               implicit accessor: Accessor[T, K],
                                                               next: Accessors.Aux[T, R, R1])
  : Accessors.Aux[T, FieldType[K, V] :: R, (T => accessor.Out) :: R1] =
    instance(accessor.get :: next.value)

  trait Accessor[T, K] {
    type Out
    def get: T => Out
  }

  object Accessor {
    type Aux[T, K, O] = Accessor[T, K] { type Out = O }

    implicit def all[T, K <: Symbol, L <: HList, O](
                                                     implicit gen: LabelledGeneric.Aux[T, L],
                                                     select: Selector.Aux[L, K, O]): Accessor.Aux[T, K, O] =
      new Accessor[T, K] {
        type Out = O
        def get: T => Out = t => select(gen.to(t))
      }
  }
}
