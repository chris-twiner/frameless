package frameless

import frameless.ops.{As, Convertible}
import org.scalacheck.Prop
import org.scalacheck.Prop._
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import frameless.ops.Flatten
import frameless.ops.Flatten._

class AsTests extends TypedDatasetSuite {
  test("as[X2[A, B]]") {
    def prop[A, B](data: Vector[(A, B)])(
      implicit
      eab: TypedEncoder[(A, B)],
      ex2: TypedEncoder[X2[A, B]]
    ): Prop = {
      val dataset = TypedDataset.create(data)

      val dataset2 = dataset.as[X2[A,B]]().collect().run().toVector
      val data2 = data.map { case (a, b) => X2(a, b) }

      dataset2 ?= data2
    }

    check(forAll(prop[Int, Int] _))
    check(forAll(prop[String, String] _))
    check(forAll(prop[String, Int] _))
    check(forAll(prop[Long, Int] _))
    check(forAll(prop[Seq[Seq[Option[Seq[Long]]]], Seq[Int]] _))
    check(forAll(prop[Seq[Option[Seq[String]]], Seq[Int]] _))
  }

  test("as underlying flatten structural equality") {

    type Tuples = ((Int, String), Float)
    type X2s = X2[X2[Int, String], Float]
    val at = implicitly[Flatten[Tuples]]
    val bt = implicitly[Flatten[X2s]]

    // prove using flattened Int :: String :: Float :: HNil and swapping flatteners for reverse works
    val a = ((1, "a"), 1.0f)
    val b = X2(X2(1, "a"), 1.0f)
    val afVal = a.flattenIt
    val bfVal = b.flattenIt

    // types are the same
    val bofAfVal = bt.reverse(afVal.asInstanceOf[bt.Out])
    val aofBfVal = at.reverse(bfVal.asInstanceOf[at.Out])

    bofAfVal shouldEqual b
    aofBfVal shouldEqual a

    val convertible = Convertible[Tuples, X2s]

    convertible.encode(a) shouldEqual b
    convertible.decode(b) shouldEqual a
  }

  test("as underlying deeply nested") {
    import frameless.ops.Flatten
    import frameless.ops.Flatten._

    // prove deeply nested roundtrips, type combos etc. via as[X2[X2...
    type Test[A,B] = X2[A,B]

    type TestType = Test[Float, Test[Test[Int, String], Test[Float, Test[String, Int]]]]

    val cci = implicitly[Flatten[TestType]]
    val cc: TestType = new Test(0.2f, new Test(new Test(1, "a"), new Test(1.0f, new Test("f", 0))))

    val cctohl = cci.apply(cc)
    val rev = cci.reverse(cctohl)

    rev shouldEqual cc
  }

  test("as[X2[X2[A, B], C]") {
    def prop[A, B, C](data: Vector[(A, B, C)])(
      implicit
      eab: TypedEncoder[((A, B), C)],
      ex2: TypedEncoder[X2[X2[A, B], C]]
    ): Prop = {
      val data2 = data.map {
        case (a, b, c) => ((a, b), c)
      }
      val dataset = TypedDataset.create(data2)

      val dataset2 = dataset.as[X2[X2[A,B], C]]().collect().run().toVector
      val data3 = data2.map { case ((a, b), c) => X2(X2(a, b), c) }

      dataset2 ?= data3
    }

    check(forAll(prop[String, Int, Int] _))
    check(forAll(prop[String, Int, String] _))
    check(forAll(prop[String, String, Int] _))
    check(forAll(prop[Long, Int, String] _))
    check(forAll(prop[Seq[Seq[Option[Seq[Long]]]], Seq[Int], Option[Seq[Option[Int]]]] _))
    check(forAll(prop[Seq[Option[Seq[String]]], Seq[Int], Seq[Option[String]]] _))
  }
}
