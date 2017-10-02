import akka.Done
import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

case class In(n: String)
case class Out(x: String)

object OneToNActor {
  def makeSlowStream(id: String): Stream[Out] = {
    (1 to 3).toStream.map(x => {
      val delay = 100
      Thread.sleep(delay)
      Out(s"$id -> $x")
    })
  }

  def makeSlowStreamFuture(n: String)(implicit ec: ExecutionContext): Future[Stream[Out]] =
    Future(makeSlowStream(n))
}

class OneToNActor(implicit ec: ExecutionContext) extends Actor {
  override def receive = {
    case In(n) =>
      sender() ! OneToNActor.makeSlowStream(n)
  }
}

object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("autodta-crawler")
  implicit val executor: ExecutionContext = actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(10.seconds)

  val actor = actorSystem.actorOf(Props(new OneToNActor)
    .withRouter(RoundRobinPool(10))
  )

  def shutdown() = {
    actorSystem.terminate()
  }

  def getNextStream(s: String) = {
    (actor ? In(s)).mapTo[Stream[Out]]
  }

  def run(): Future[Done] = {
    val start = System.currentTimeMillis()
    val concurrency = 2
    Source(
      (1 to 4).toStream.map(i => {
        println(s"1: Emitting $i")
        i.toString
      }))
      .mapAsyncUnordered(concurrency)(getNextStream)
      .mapConcat(identity)
      .mapAsyncUnordered(concurrency)(out => getNextStream(out.x))
      .mapConcat(identity)
      .mapAsyncUnordered(concurrency)(out => getNextStream(out.x))
      .mapConcat(identity)
      .map(x => println(s"4: Received $x after ${System.currentTimeMillis() - start}"))
      .runWith(Sink.ignore)
  }

  println("Awaiting")
  Await.result(run().flatMap(_ => shutdown()), 20.seconds)
  println("Done")
}
