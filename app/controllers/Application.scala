package controllers

import java.util.concurrent.Executors

import models.Event
import models.Github.UserSummary
import play.api.http.MimeTypes
import play.api.mvc._
import rx.lang.scala.Observable
import services.Github
import util.RxHelpers.observable2Enumerator

import scala.concurrent.ExecutionContext.Implicits.global

class Application extends Controller {

  def index = Action {
    Ok(views.html.index())
  }

  def react = Action {
    Ok(views.html.react())
  }

  def polymer = Action {
    Ok(views.html.polymer())
  }

  private def networkUserEvent(userSummary: UserSummary) = Observable.from(Github.getUser(userSummary).map(Event.addToUserNetworkUser))

  def githubUser(username: String): Action[AnyContent] = Action { implicit request =>
    val userEvents = Github.getUser(username).map {
      user =>
        val following = Github.getFollowing(user)
        val followers = Github.getFollowers(user)
        val repos = Github.getRepos(user)
        val starred = Github.getStarred(user)

        Observable.just(Event.loadUser(user))
          .merge {
            following.flatMap(networkUserEvent)
          }
          .merge {
            followers.flatMap(networkUserEvent)
          }
          .merge {
            repos.flatMap {
              repo =>
                val stargazers = Github.getStargazers(repo)
               // stargazers.size.map(Event.setStargazerCount(user, _)).merge {
                  stargazers.flatMap(networkUserEvent)
                //}
            }
          }
        .merge {
          starred.size.map(Event.setStarredRepoCount(user, _)).merge {
            starred.flatMap {
              starredRepo =>
                networkUserEvent(starredRepo.userSummary)
            }
          }
        }
    }
    val events = Observable.from(userEvents).flatten

    /*
        val events = Observable.from(WS.url(userUrl).execute("GET").filter(_.status == OK)).flatMap {
          response =>
            val json = Json.parse(response.body)
            val user = json.as[User]
            Observable.just(Event("user" -> user)).merge {
              val repos = services.Github.getRepos(user.reposUrl)
              repos.map(Event("loadRepos", _)).merge {
                repos.flatMap(repo => services.Github.getIssues(repo).map(issue => Event("loadRepoIssue", RepoIssue(repo.id, issue))))
              }
            }
          }
*/
    /*
        val user = User(4438794, "stevenrskelton", "Steven Skelton", None, Some("http://stevenskelton.ca"), Some("Toronto, Canada"), "https://avatars.githubusercontent.com/u/4438794?v=3", "https://api.github.com/users/stevenrskelton/repos", 12, 16,"https://api.github.com/users/stevenrskelton/followers", 0, "https://api.github.com/users/stevenrskelton/following")
        val repos = Seq(
          Repo(12982485, "Blog", "stevenrskelton/Blog", "Coding examples found on http://stevenskelton.ca", false, "2014-06-26T04:54:40Z", 0, "Scala", 0, "", false, 0),
          Repo(17383764, "d3-geomap", "stevenrskelton/d3-geomap", "Polymer Web Component for geographic topology visualization", false, "2014-06-26T04:54:40Z", 0, "Scala", 0, "", false, 0),
          Repo(17411171, "datamaps", "stevenrskelton/datamaps", "Customizable SVG map visualizations for the web in a single Javascript file using D3.js", false, "2014-06-26T04:54:40Z", 0, "Scala", 0, "", false, 0),
          Repo(16363788, "Fish-UI", "stevenrskelton/Fish-UI", "Aquarium research and purchasing tool built using Polymer Web Components..", false, "2014-06-26T04:54:40Z", 0, "Scala", 0, "", false, 0)
        ).map(r => Event("loadRepos" -> r))
        val events = Observable.just(Seq(Event("user" -> user)) ++ Seq(Event("user-network" -> user.copy(id=1)),Event("user-network" -> user.copy(id=2)),Event("user-network" -> user.copy(id=3))) /*++ repos*/ : _*)
    */
    //val eventEnumerator: Enumerator[Event] = observable2Enumerator(events) //.map(_.getBytes)


    //Ok.feed(eventEnumerator).as(MimeTypes.EVENT_STREAM)
    //Ok.chunked(eventEnumerator.map(_.data + "\n\n")).as(MimeTypes.EVENT_STREAM)
    Ok.chunked(events).withHeaders(Github.rateLimitHeaders: _*).as(MimeTypes.EVENT_STREAM)
  }

}