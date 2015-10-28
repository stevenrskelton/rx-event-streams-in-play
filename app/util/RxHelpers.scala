package util

import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.{Input, Concurrent, Enumerator}
import rx.lang.scala.Observable

import scala.concurrent.ExecutionContext

object RxHelpers {
  /*
  * Observable to Enumerator
  */
  implicit def observable2Enumerator[T](obs: Observable[T])(implicit executionContext: ExecutionContext): Enumerator[T] = {

    // unicast create a channel where you can push data and returns an Enumerator
    Concurrent.unicast { channel =>
      val subscription = obs.subscribe(new ChannelObserver(channel))
      val onComplete = { () => subscription.unsubscribe }
      val onError = { (_: String, _: Input[T]) => subscription.unsubscribe }
      (onComplete, onError)
    }
  }

  private class ChannelObserver[T](channel: Channel[T]) extends rx.lang.scala.Observer[T] {
    override def onNext(elem: T): Unit = channel.push(elem)

    override def onCompleted(): Unit = channel.end()

    override def onError(e: Throwable): Unit = channel.end(e)
  }
}
