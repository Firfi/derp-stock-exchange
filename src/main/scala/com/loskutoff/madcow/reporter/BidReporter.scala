package com.loskutoff.madcow.reporter

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshallable}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.{Decoder, Encoder, Json}
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Random

object BidKind extends Enumeration {
  type BidKind = Value
  val Buy = Value("Buy")
  val Sell = Value("Sell")
}
import BidKind._
case class Bid(price: Double, qty: Int, kind: BidKind)
object Bid {
  implicit val encodeBid: Encoder[Bid] =
    Encoder.instance((b: Bid) => Json.obj(
      "price"->Json.fromDouble(b.price).get,
      "kind"->Json.fromString(b.kind.toString),
      "qty"->Json.fromInt(b.qty)
    ))
}

trait Protocols extends DefaultJsonProtocol with CirceSupport {

}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  lazy val reportConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("report.host"), config.getInt("report.port"))

  def reportRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(reportConnectionFlow).runWith(Sink.head)

  def report(bids: Seq[Bid]): Unit = {
    reportRequest(RequestBuilding.Post(s"/report", bids))
  }

}

class BidReporter extends Service { // should be Object extending App inside the different app. It is here for simplicity
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  val biddersCrowd = 10
  def priceLaw(k: Int) = (Math.sin(k * 0.1) + 2) * 100
  val margin = 6
  def priceFluctuation = Math.random() * margin - margin/2 + 1 // no 0 price
  def step(n: Int): Unit = {
    def bidFromStep = priceLaw(n) + priceFluctuation
    val bids = (0 to Math.ceil(Math.random() * biddersCrowd).toInt).map(_ => bidFromStep)
    val chunked = bids.sorted.grouped(Math.ceil(bids.length / 2).toInt).toArray
    def qty = Math.ceil(Math.random() * 100).toInt
    report(
      Random.shuffle(
        chunked(0).map(v => Bid(v, qty, Buy))
          ++ chunked(1).map(v => Bid(v, qty, Sell))
      )
    )
    akka.pattern.after(2.second, using = system.scheduler) {
      Future { step(n + 1) }
    }
  }

  step(0)

}