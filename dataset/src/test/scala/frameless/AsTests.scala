package frameless

import frameless.ops.{As, Convertible}
import org.scalacheck.Prop
import org.scalacheck.Prop._
import shapeless.{::, Generic, HList, HNil}
import shapeless._
import shapeless.ops.hlist.Align

class AsTests extends TypedDatasetSuite {
  test("as[X2[A, B]]") {
    def prop[A, B](data: Vector[(A, B)])(
      implicit
      eab: TypedEncoder[(A, B)],
      ex2: TypedEncoder[X2[A, B]]
    ): Prop = {
      val dataset = TypedDataset.create(data)

    //  val g = implicitly[Generic.Aux[(A,B), A::B::HNil]]
  //    val f = implicitly[Generic.Aux[X2[A,B], A::B::HNil]]

//      implicit val a = implicitly[Convertible[(A,B), X2[A,B], A::B::HNil, A::B::HNil]]

      val d = As.deriveAs[(A,B), X2[A,B], Generic.Aux[(A,B), A::B::HNil]#Repr, Generic.Aux[X2[A,B],A::B::HNil]#Repr]
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

  test("as[X2[X2[A, B], C]") {

/*
        implicit val a = implicitly[Convertible[(Int,String), X2[Int,String], Int::String::HNil, Int::String::HNil]]

        implicit val ug = implicitly[Align[Int::String::HNil, Int::String::HNil]]

        implicit val ub = implicitly[Align[(Int,String)::Int::HNil, (Int,String)::Int::HNil]]
        implicit val ubx = implicitly[Align[(Int,String)::HNil, X2[Int,String]::HNil]]
        implicit val ubx = implicitly[Align[(Int,String)::Int::HNil, X2[Int,String]::Int::HNil]]
*/
   //     implicit val b = implicitly[Convertible[((Int,String), Int), X2[X2[Int,String], Int], (Int,String)::Int::HNil, X2[Int,String]::Int::HNil]]
    /* def prop[A, B, C](data: Vector[(A, B, C)])(
      implicit
      eab: TypedEncoder[((A, B), C)],
      ex2: TypedEncoder[X2[X2[A, B], C]]
    ): Prop = {
      val data2 = data.map {
        case (a, b, c) => ((a, b), c)
      }
      val dataset = TypedDataset.create(data2)

      // (genA.to((("s",1),2)), genB.to(X2(X2("s",1),2)))
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


    val aDeep = DeepHLister[((Int,String), Float) :: HNil]
    val bDeep = DeepHLister[X2[X2[Int, String], Float] :: HNil]

    val typedA: DeepHLister[((Int,String), Float) :: HNil] {
      type Out = (Int :: String :: HNil) :: Float :: HNil
    } = aDeep
*/
  /*  import shapeless._
    import ops.tuple.FlatMapper
    import syntax.std.tuple._*/


    //val aVal = frameless.ops.flatten(((1,"a"), 1.0f))
    //val bVal = frameless.ops.flatten(X2(X2(1,"a"), 1.0f))

    import frameless.ops.Flatten
    import frameless.ops.Flatten._


    val at = implicitly[Flatten[((Int,String),Float)]]
    val bt = implicitly[Flatten[X2[X2[Int, String], Float]]]

    val afVal = ((1,"a"), 1.0f).flattenIt
    val bfVal = X2(X2(1,"a"), 1.0f).flattenIt

    type Test[A,B] = Tuple2[A,B]

    val cci = implicitly[Flatten[Test[Int, String]]]
    val cc = new Test(1, "a")

    val cctohl = cci.apply(cc)
    val rev = cci.reverse(cctohl)

    println(s"cctohl $cctohl  rev $rev")


    // types are the same
    //    val bofAfVal = bt.reverse(afVal.asInstanceOf[bt.Out])
    //  val aofBfVal = at.reverse(bfVal.asInstanceOf[at.Out])
    //println(s"bofAfVal $bofAfVal  -  aofBfVal $aofBfVal")
  }
}
