package play.api.libs.akka

import akka.dispatch.{ Future, Await }
import akka.actor.ActorSystem

import play.api.libs.concurrent._
import java.util.concurrent.TimeUnit
import com.typesafe.config.ConfigFactory

object Akka {
  private lazy val actorSystemName: String = play.api.Play.maybeApplication.map(_.configuration.getString("akka.default.system.name")).flatMap(x=>x).getOrElse("playcore")
  lazy val system = ActorSystem(actorSystemName, ConfigFactory.load.getConfig(actorSystemName))

  implicit def akkaToPlay[A](future: Future[A]) = new AkkaFuture(future)
}


/**
 * Wrapper used to transform an Akka Future to Play Promise
 */
class AkkaFuture[A](future: Future[A]) {
  def asPromise: Promise[A] = new AkkaPromise(future)
}

/**
 * a promise impelemantation based on Akka's Future
 */
class AkkaPromise[A](future: Future[A]) extends Promise[A] {

  /**
   * call back hook
   */
  def onRedeem(k: A => Unit) {
    future.onComplete { _.fold(Thrown(_), k) }
  }

  /*
   * extend @param k 
   */
  def extend[B](k: Function1[Promise[A], B]): Promise[B] = {
    val p = Promise[B]()
    future.onSuccess { case a => p.redeem(k(this)) }
    future.onFailure { case e => p.redeem(k(this)) }
    p
  }

  /*
   * it's time to retrieve the future value
   */
  def await(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): NotWaiting[A] = {
    try {
      Redeemed(Await.result(future,akka.util.Duration(timeout, unit)))
    } catch {
      case e => Thrown(e)
    }
  }

  /*
   * filtering akka based future and rewrapping the result in an AkkaPromise
   */
  def filter(p: A => Boolean): Promise[A] =
    new AkkaPromise[A](future.filter(p.asInstanceOf[(Any => Boolean)]).asInstanceOf[Future[A]])

  /*
   * mapping @param f function to AkkaPromise 
   *
   */
  def map[B](f: A => B): Promise[B] = new AkkaPromise[B](future.map(f))

  /**
   * provides a means to flatten Akka based promises
   */
  def flatMap[B](f: A => Promise[B]): Promise[B] = {
    val result = Promise[B]()
    future.map(f(_).map(result.redeem(_)))
    result
  }
}
