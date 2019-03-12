package starfruit.util

import language.{higherKinds}
import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom

object Morphisms {

  class Ana[C[_]](val _i: Morphisms.type) extends AnyVal {
    def apply[A, B](b: B)(pred: B => Boolean)(gen: B => (A, B))(implicit cbf: CanBuildFrom[Nothing, A, C[A]]): C[A] = {
      val builder = cbf()
      @tailrec def h(b: B): Unit = if (!pred(b)) {
        val (a, b2) = gen(b)
        builder += a
        h(b2)
      }
      h(b)
      builder.result()
    }
  }
  def ana[C[_]] = new Ana[C](this)
}