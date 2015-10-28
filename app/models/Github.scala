package models

import play.api.libs.json.{Json, JsSuccess, JsValue, Reads}

object Github {

  case class UserSummary(id: Long,
                         username: String,
                         url: String,
                         avatarUrl: String,
                         reposUrl: String,
                         followersUrl: String,
                         followingUrl: String)

  case class User(id: Long,
                  username: String,
                  url: String,
                  name: Option[String],
                  company: Option[String],
                  blog: Option[String],
                  location: Option[String],
                  avatarUrl: String,
                  starredUrl: String,
                  reposUrl: String,
                  numberOfRepos: Int,
                  followers: Int,
                  followersUrl: String,
                  following: Int,
                  followingUrl: String)

  case class Repo(id: Long,
                  name: String,
                  fullName: String,
                  description: String,
                  userSummary: UserSummary,
                  fork: Boolean,
                  updated: String,
                  starsgazers: Int,
                  stargazersUrl: String,
                  language: String,
                  forks: Int,
                  issuesUrl: String,
                  hasIssues: Boolean,
                  openIssues: Int)

  case class Issue(url: String,
                   number: Int,
                   title: String,
                   user: String,
                   isOpen: Boolean,
                   body: String)

  case class RepoIssue(repoId: Long, issue: Issue)

  implicit def cleanUrlParam(str: String): String = {
    val i = str.indexOf('{')
    if (i == -1) str
    else str.substring(0, i)
  }

  implicit val userSummaryReads = new Reads[UserSummary] {
    def reads(json: JsValue) = JsSuccess(
      new UserSummary(
        (json \ "id").as[Long],
        (json \ "login").as[String],
        (json \ "url").as[String],
        (json \ "avatar_url").as[String],
        (json \ "repos_url").as[String],
        (json \ "followers_url").as[String],
        cleanUrlParam((json \ "following_url").as[String]))
    )
  }

  implicit val userSummaryWrites = Json.writes[UserSummary]

  implicit val userReads = new Reads[User] {
    def reads(json: JsValue) = JsSuccess(
      new User(
        (json \ "id").as[Long],
        (json \ "login").as[String],
        (json \ "url").as[String],
        (json \ "name").asOpt[String],
        (json \ "company").asOpt[String],
        (json \ "blog").asOpt[String],
        (json \ "location").asOpt[String],
        (json \ "avatar_url").as[String],
        cleanUrlParam((json \ "starred_url").as[String]),
        (json \ "repos_url").as[String],
        (json \ "public_repos").asOpt[Int].getOrElse(0),
        (json \ "followers").asOpt[Int].getOrElse(0),
        (json \ "followers_url").as[String],
        (json \ "following").asOpt[Int].getOrElse(0),
        cleanUrlParam((json \ "following_url").as[String]))
    )
  }

  implicit val userWrites = Json.writes[User]

  implicit val reposReads = new Reads[Repo] {
    def reads(json: JsValue) = JsSuccess(
      new Repo(
        (json \ "id").as[Long],
        (json \ "name").as[String],
        (json \ "full_name").as[String],
        (json \ "description").as[String],
        (json \ "owner").as[UserSummary],
        (json \ "fork").as[Boolean],
        (json \ "updated_at").as[String],
        (json \ "stargazers_count").as[Int],
        (json \ "stargazers_url").as[String],
        (json \ "language").as[String],
        (json \ "forks_count").as[Int],
        (json \ "issues_url").as[String].replaceAllLiterally("{/number}", ""),
        (json \ "has_issues").as[Boolean],
        (json \ "open_issues").as[Int])
    )
  }

  implicit val repoWrites = Json.writes[Repo]

  implicit val issueReads = new Reads[Issue] {
    def reads(json: JsValue) = JsSuccess(
      new Issue(
        (json \ "url").as[String],
        (json \ "number").as[Int],
        (json \ "title").as[String],
        (json \ "user" \ "login").as[String],
        (json \ "state").as[String] == "open",
        (json \ "body").as[String])
    )
  }

  implicit val issueWrites = Json.writes[Issue]

  implicit val repoIssueFormat = Json.format[RepoIssue]
}
