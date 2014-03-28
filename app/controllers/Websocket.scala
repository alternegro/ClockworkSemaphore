package controllers

import play.api.mvc.{WebSocket => PlayWS, RequestHeader, Controller}
import service.RedisServiceLayerImpl
import play.api.libs.json._
import actors.SocketActor
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import scala.concurrent.Future
import akka.event.slf4j.Logger
import play.api.libs.concurrent.Execution.Implicits._
import akka.util.Timeout
import play.api.libs.concurrent.Akka
import akka.actor.Props
import akka.util.Timeout
import scala.concurrent.duration._
import entities.{AuthToken, UserId}
import utils.Utils._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import scala.Some
import play.api.libs.json.JsNumber
import play.api.Play.current
import akka.actor.Props
import akka.pattern.ask
import akka.event.slf4j.Logger

object WebSocket extends Controller with RedisServiceLayerImpl  {

  implicit val timeout = Timeout(2 second)
  val socketActor = Akka.system.actorOf(Props[SocketActor])

  def get_auth_token(req: RequestHeader) =
    req.session.get("login").map{t => AuthToken(t)}

  // todo: ...duplicate code :(
  def authenticate(req: RequestHeader): Future[UserId] = {
    for {
      token <- match_or_else(get_auth_token(req), "auth string not found"){ case Some(t) => t}
      uid <- redisService.user_from_auth_token(token)
    } yield uid
  }

  /**
   * This function creates a WebSocket using the
   * enumerator linked to the current user,
   * retrieved from the TaskActor.
   */
  def indexWS = PlayWS.async[JsValue] {
    implicit requestHeader =>
      for {
        uid <- authenticate(requestHeader)
        enumerator <- (socketActor ? SocketActor.StartSocket(uid))
      } yield {
        val it = Iteratee.foreach[JsValue]{
          case JsObject(Seq( ("msg", JsString(msg)) )) =>
            socketActor ! SocketActor.MakePost(uid, msg)

          case JsObject(Seq( ("feed", JsString("my_feed")), ("page", JsNumber(page)))) =>
            log.info(s"load feed for user $uid page $page for user $uid")
            for {
              feed <- redisService.get_user_feed(uid, page.toInt)
            } socketActor ! SocketActor.SendMessages("my_feed", uid, feed)


          case JsObject(Seq( ("feed", JsString("global_feed")), ("page", JsNumber(page)))) =>
            log.info(s"load global feed page $page for user $uid")
            for {
              feed <- redisService.get_global_feed(page.toInt)
            } socketActor ! SocketActor.SendMessages("global_feed", uid, feed)

          case JsString("ping") =>

          case js => log.error(s"  ???: received invalid jsvalue $js")

        }.mapDone {
          _ => socketActor ! SocketActor.SocketClosed(uid)
        }

        (it, enumerator.asInstanceOf[Enumerator[JsValue]])
      }

  }

  def errorFuture = {
    val in = Iteratee.ignore[JsValue]
    val out = Enumerator(Json.toJson("not authorized")).andThen(Enumerator.eof)

    Future {
      (in, out)
    }
  }

}
