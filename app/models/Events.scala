package models

import models.Github.User
import play.api.libs.EventSource.{Event => EventSourceEvent, EventDataExtractor}
import play.api.libs.json.{Json, Writes}

object Event {

  implicit def defaultDataExtractor[T](implicit writes: Writes[T]) = new EventDataExtractor[T](e => Json.stringify(Json.toJson(e)))

  implicit def pairEventDataExtractor[T](implicit writes: Writes[T]) = EventDataExtractor[(String, T)](t => Json.stringify(Json.toJson(t._2)))

  def loadUser(user: User): EventSourceEvent = EventSourceEvent("user" -> user)

  def addToUserNetworkUser(user: User): EventSourceEvent = EventSourceEvent("user-network" -> user)

  def setStarredRepoCount(user: User, starredRepos: Int) = EventSourceEvent("starred" -> Json.obj("userId" -> user.id, "repoCount" -> starredRepos))
}