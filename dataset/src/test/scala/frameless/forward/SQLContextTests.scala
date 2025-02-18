package frameless

import org.apache.spark.sql.classic
import org.scalacheck.Prop
import org.scalacheck.Prop.{ forAll, _ }

class SQLContextTests extends TypedDatasetSuite {
  test("sqlContext") {
    def prop[A: TypedEncoder](data: Vector[A]): Prop = {
      val dataset = TypedDataset.create[A](data)
      // todo - same as Dataset, can't exist on base interface, we need to match etc.
      dataset.sqlContext =? dataset.dataset
        .asInstanceOf[classic.Dataset[A]]
        .sqlContext
    }

    check(forAll(prop[Int] _))
    check(forAll(prop[String] _))
  }
}
