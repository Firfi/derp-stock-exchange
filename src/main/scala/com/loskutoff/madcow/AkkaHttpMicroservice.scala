package com.loskutoff.madcow

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import com.loskutoff.madcow.reporter.BidReporter
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.{Decoder, Encoder, Json}
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}

trait Publisher extends CirceSupport {
  def publishDeal(d: Deal): Unit
  def publishBid(b: DBBid): Unit
  def publishBidChange(b: DBBid): Unit
}

trait PubNubPublisher extends Publisher {
  import io.circe.syntax._
  import com.pubnub.api.{Pubnub, Callback, PubnubError}
  val pubnub = new Pubnub("pub-c-8eb80bba-400f-4167-89d4-6a49c7516c85","sub-c-c655cf06-3794-11e6-885b-02ee2ddab7fe")
  private def _publish[A](entity: A, channel: String)(implicit e: Encoder[A]): Unit = {
    val callback = new Callback() {
      override def successCallback(channel: String, response: Object): Unit = {}

      override def errorCallback(channel: String, pubnubError: PubnubError): Unit =
        println(pubnubError.getErrorString)
    }
    pubnub.publish(channel, entity.asJson.spaces2, callback)
  }
  override def publishDeal(d: Deal): Unit = _publish(d, "deals")
  override def publishBid(b: DBBid): Unit = _publish(b, "bids")
  override def publishBidChange(b: DBBid): Unit = _publish(b, "bidChanges")
}

object BidKind extends Enumeration {
  type BidKind = Value
  val Buy = Value("Buy")
  val Sell = Value("Sell")
}
import BidKind._
case class Bid(price: Double, qty: Int, kind: BidKind)
object Bid {
  implicit val decodeBid: Decoder[Bid] =
    Decoder.instance(j =>
      for {
        price <- j.downField("price").as[Double]
        qty <- j.downField("qty").as[Int]
        kind <- j.downField("kind").as[String]
      } yield Bid(price, qty, BidKind.withName(kind))
    )
  implicit val encodeBid: Encoder[Bid] =
    Encoder.instance((b: Bid) => Json.obj(
      "price"->Json.fromDouble(b.price).get,
      "qty"->Json.fromInt(b.qty),
      "kind"->Json.fromString(b.kind.toString)
    ))
}
case class DBBid(bid: Bid, id: String) extends Ordered[DBBid] {
  override def compare(that: DBBid): Int = Ordering.Double.compare(that.bid.price, this.bid.price)
}
object DBBid {
  import io.circe.syntax._
  implicit val encodeDBBid: Encoder[DBBid] =
    Encoder.instance((b: DBBid) => Json.obj(
      "bid"->b.bid.asJson,
      "id"->Json.fromString(b.id)
    ))
}

case class Deal(buyBid: DBBid, sellBid: DBBid, price: Double, qty: Int)
object Deal {
  import io.circe.syntax._
  implicit val encodeDeal: Encoder[Deal] =
    Encoder.instance((d: Deal) => Json.obj(
      "buyBid"->d.buyBid.asJson,
      "sellBid"->d.sellBid.asJson,
      "qty"->Json.fromInt(d.qty),
      "price"->Json.fromDouble(d.price).get
    ))
}

trait Protocols extends DefaultJsonProtocol with CirceSupport {

}

class ValueReporter(service: Service) {
  implicit val executionContext = service.executor
  val c = new Contract()
  akka.pattern.after(10.second, using = service.system.scheduler) {
    Future { c.sendValue((System.currentTimeMillis / 1000).toInt, totalValue()) }
  }
  def totalValue(): Int = (service.sellBids ++ service.buyBids).map(b => b.bid.qty).sum
}

trait Bids extends PubNubPublisher {

  // assumption: most highest bidder will take least high bid
  val sellBids = mutable.PriorityQueue[DBBid]() // PriorityQueue - heap, treelike structure.
  val buyBids = mutable.PriorityQueue[DBBid]().reverse

  def deal(activeBid: DBBid, dbBid: DBBid, price: Double, qty: Int): Unit = {
    val (buyBid, sellBid) = activeBid.bid.kind match {
      case Buy => (activeBid, dbBid)
      case Sell => (dbBid, activeBid)
    }
    publishDeal(Deal(buyBid, sellBid, price, qty))
  }

  // I know it's almost identical to 'sell'. I'll merge them if will have time
  def makeBid(dbBid: DBBid): Unit = {
//    println(s"sellBids ${sellBids.length} buyBids ${buyBids.length}")
//    println(s"bid kind: ${dbBid.bid.kind.toString}")
    val bid = dbBid.bid
    val (retrieveQueue, addQueue, comparison, dealPrice) = bid.kind match {
      case Buy => (sellBids, buyBids, (p1: Double, p2: Double) => p1 <= p2,
        (newBid: Bid, retrievedBid: Bid) => retrievedBid.price)
      case Sell => (buyBids, sellBids, (p1: Double, p2: Double) => p1 >= p2,
        (newBid: Bid, retrievedBid: Bid) => newBid.price)
    }
    def _bid(qty: Int): Unit = {
//      if (retrieveQueue.nonEmpty) {
//        println(s"retrieved price: ${retrieveQueue.head.bid.price}, price for ${dbBid.bid.kind.toString}: ${dbBid.bid.price}")
//        println(s"somparison: ${comparison(retrieveQueue.head.bid.price, bid.price)}")
//      }

      if (retrieveQueue.nonEmpty && comparison(retrieveQueue.head.bid.price, bid.price)) {
        val retrievedBid = retrieveQueue.dequeue
        val retrievedQty = retrievedBid.bid.qty
//        println(s"not empty. last price: ${retrievedBid.bid.price}, last qty: ${retrievedBid.bid.qty}, retrieved bid type: ${retrievedBid.bid.kind.toString}")
        val diff = qty - retrievedQty
//        println(s"qty ${qty}, diff ${diff}, retrievedQty $retrievedQty")
        if (diff == 0) {
          deal(retrievedBid, dbBid, dealPrice(bid, retrievedBid.bid), retrievedQty)
        } else if (diff > 0) {
          deal(retrievedBid, dbBid, dealPrice(bid, retrievedBid.bid), retrievedQty)
          _bid(diff)
        } else if (diff < 0) {
          deal(retrievedBid, dbBid, dealPrice(bid, retrievedBid.bid), qty)
          val newBid = DBBid(Bid(retrievedBid.bid.price, Math.abs(diff), retrievedBid.bid.kind), retrievedBid.id)
          publishBidChange(newBid)
          addQueue.enqueue(newBid)
        }
      } else {
        publishBid(dbBid)
        addQueue.enqueue(dbBid)
      }
    }
    _bid(dbBid.bid.qty)
  }
}

trait Service extends Protocols with Bids {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  val routes = {
    logRequestResult("akka-http-microservice") {
      path("report") {
        post {
          entity(as[Seq[Bid]]) { bids =>
            for (bid <- bids) yield {
              makeBid(DBBid(bid, java.util.UUID.randomUUID.toString))
            }
            complete {
              "ok"
            }
          }
        }
      }
    }
  }
}

object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  new BidReporter()
  new ValueReporter(this)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
