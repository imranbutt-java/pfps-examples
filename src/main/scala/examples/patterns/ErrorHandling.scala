package examples.patterns

import scala.util.control.NoStackTrace

import cats._
import cats.effect._
import cats.implicits._

case class Category(name: String)

trait Random[F[_]] {
  def bool: F[Boolean]
  def int: F[Int]
}

object Random {
  def apply[F[_]: Random]: Random[F] = implicitly

  implicit def syncInstance[F[_]: Sync]: Random[F] =
    new Random[F] {
      def bool: F[Boolean] = int.map(_ % 2 === 0)
      def int: F[Int]      = Sync[F].delay(scala.util.Random.nextInt(100))
    }
}

sealed trait BusinessError extends NoStackTrace
case object RandomError    extends BusinessError
//case object AnotherError extends BusinessError // uncomment to see issue

trait Categories[F[_]] {
  def findAll: F[List[Category]]
  def maybeFindAll: F[Either[BusinessError, List[Category]]]
}

class LiveCategories[F[_]: MonadThrow: Random] extends Categories[F] {

  def findAll: F[List[Category]] =
    Random[F].bool.ifM(
      List.empty[Category].pure[F],
      RandomError.raiseError[F, List[Category]]
    )

  def maybeFindAll: F[Either[BusinessError, List[Category]]] =
    Random[F].bool.map {
      case true  => List.empty[Category].asRight[BusinessError]
      case false => RandomError.asLeft[List[Category]]
    }

}

class Program[F[_]: Functor](
    categories: Categories[F]
) {

  def findAll: F[List[Category]] =
    categories.maybeFindAll.map {
      case Right(c)          => c
      case Left(RandomError) => List.empty[Category]
    }

}

class SameProgram[F[_]: ApplicativeThrow](
    categories: Categories[F]
) {

  def findAll: F[List[Category]] =
    categories.findAll.recover { case RandomError =>
      List.empty[Category]
    }

}
