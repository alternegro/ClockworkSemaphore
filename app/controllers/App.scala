
package controllers

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.concurrent.Future
import actors._

import play.api.Routes

import service._
import entities._
import utils.Utils._
import scala.util.{Failure, Success}
import akka.event.slf4j.Logger

object App extends Controller with RedisServiceLayerImpl {

  def index = Authenticated.async {
    implicit request => {
      val user_id = request.user

      for {
        username <- redisService.get_user_name(user_id)
      } yield Ok(views.html.app.index(user_id.uid, username))
    }
  }

}
























