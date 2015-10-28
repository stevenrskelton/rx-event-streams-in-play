package services

import java.util.concurrent.Executors

import models.Github._
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.{WS, WSResponse}
import rx.lang.scala.Observable

import scala.concurrent.{ExecutionContext, Future}

object Github extends play.api.http.Status {

  //limit the number of concurrent Github API requests to 16
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))

  var rateLimitLimit: Long = 0
  var rateLimitRemaining: Long = Long.MaxValue
  var rateLimitReset: Long = 0

  def rateLimitHeaders: Seq[(String, String)] =
    if (rateLimitReset == 0) Nil
    else Seq(
      "X-RateLimit-Limit" -> rateLimitLimit.toString,
      "X-RateLimit-Remaining" -> rateLimitRemaining.toString,
      "X-RateLimit-Reset" -> rateLimitReset.toString
    )

  val cache = play.api.cache.Cache

  case class EtagResponse(etag: String, body: JsValue)

  private def setHeaders(response: WSResponse) = {
    response.header("X-RateLimit-Limit").foreach(s => rateLimitLimit = s.toLong)
    response.header("X-RateLimit-Remaining").foreach(s => rateLimitRemaining = s.toLong)
    response.header("X-RateLimit-Reset").foreach(s => rateLimitReset = s.toLong)
  }

  private def transformAndCacheResponse[T](response: WSResponse, url: String, transform: JsValue => T): T = {
    setHeaders(response)
    val etag = response.header("Etag").getOrElse("")
    val body = Json.parse(response.body)
    cache.set(url, EtagResponse(etag, body))
    transform(body)
  }

  private def getEtagCache[T](url: String, transform: JsValue => T): Future[T] = {
    if (rateLimitRemaining == 0 && rateLimitReset > System.currentTimeMillis) {
      cache.getAs[EtagResponse](url).fold {
        println(s"${(rateLimitReset - System.currentTimeMillis) / 60000} minutes remaining")
        Future.failed[T](new Exception("Open Circuit Breaker"))
      } {
        etagResponse => Future.successful(transform(etagResponse.body))
      }
    } else {
      cache.get(url).fold {
        WS.url(url).execute("GET").collect {
          case response if response.status == OK =>
            println("GET: " + url)
            transformAndCacheResponse(response, url, transform)
          case response if response.status == FORBIDDEN =>
            println("REJECT 403: " + url)
            (Json.parse(response.body) \ "message").asOpt[String].foreach(println)
            setHeaders(response)
            return Future.failed(new Exception(response.statusText))
          case response =>
            println(s"${response.statusText} ${response.status.toString}: $url")
            println(response.body)
            setHeaders(response)
            return Future.failed(new Exception(response.statusText))
        }
      } {
        case EtagResponse(etag, cachedResponse) =>
          WS.url(url).withHeaders("If-None-Match" -> etag).execute("GET").collect {
            case response if response.status == NOT_MODIFIED =>
              setHeaders(response)
              transform(cachedResponse)
            case response if response.status == OK =>
              println("NEW: " + url)
              transformAndCacheResponse(response, url, transform)
            case response if response.status == FORBIDDEN =>
              println("REJECT 403: " + url)
              (Json.parse(response.body) \ "message").asOpt[String].foreach(println)
              setHeaders(response)
              transform(cachedResponse)
          }
      }
    }
  }

  private def getUrl[T](url: String)(implicit reads: Reads[T]): Future[T] = {
    getEtagCache(url, _.as[T])
  }

  private def getUrlList[T](url: String)(implicit reads: Reads[T]): Observable[T] = {
    val futureList = getEtagCache(url, _.as[List[T]])
    Observable.from(futureList).flatMap(list => Observable.just(list: _*))
  }

  def getUser(username: String): Future[User] = getUrl[User](s"https://api.github.com/users/$username")

  def getUser(userSummary: UserSummary): Future[User] = getUser(userSummary.username)

  def getFollowers(user: User): Observable[UserSummary] = getUrlList[UserSummary](user.followersUrl)

  def getFollowing(user: User): Observable[UserSummary] = getUrlList[UserSummary](user.followingUrl)

  def getRepos(user: User): Observable[Repo] = getUrlList[Repo](user.reposUrl)

  def getStargazers(repo: Repo): Observable[UserSummary] = getUrlList[UserSummary](repo.stargazersUrl)

  def getStarred(user: User): Observable[Repo] = getUrlList[Repo](user.starredUrl)

  /*
  def getUser(userUrl: String): Observable[Event] =
    Observable.from(WS.url(userUrl).execute("GET").filter(_.status == OK)).flatMap {
      response =>
        val json = Json.parse(response.body)
        val user = json.as[User]
        val observable = Observable.just(Event("user",user))
        if (user.numberOfRepos > 0) {
          val repoUrl = (json \ "repos_url").as[String]
          observable.merge(getRepos(repoUrl))
        }else {
          observable
        }
    }
    */


  /*
  def getRepos(repoUrl: String): Observable[Event] =
    Observable.from(WS.url(repoUrl).execute("GET").filter(_.status == OK)).flatMap {
      response =>
        val repos = Json.parse(response.body).as[List[Repo]].sortBy(_.openIssues * -1)
        Observable.just(repos: _*).flatMap {
          case repo if repo.openIssues == 0 =>
            Observable.just(Event("repo",repo))
          case repo =>
            val issues = getRepoIssues(repo.issuesUrl, repo.id)
            Observable.just(Event("repo",repo)).merge(issues)
        }
    }
*/

  /*
  def getRepoIssues(issuesUrl: String, repoId: Long): Observable[Event] =
    Observable.from(WS.url(issuesUrl).execute("GET").filter(_.status == OK)).flatMap {
      response =>
        val issues = Json.parse(response.body).as[List[Issue]].sortBy(_.isOpen).sortBy(_.number)
        val issueEvents = issues.map(issue => Event("repoIssue", new RepoIssue(repoId, issue)))
        Observable.from(issueEvents)
    }

  def getIssues(repo: Repo): Observable[Issue] = {
    if (repo.hasIssues) {
      Observable.from(WS.url(repo.issuesUrl).execute("GET").filter(_.status == OK)).flatMap {
        response =>
          val issues = Json.parse(response.body).as[List[Issue]].sortBy(_.isOpen).sortBy(_.number)
          Observable.just(issues: _*)
      }
    } else Observable.empty
  }
  */
}
