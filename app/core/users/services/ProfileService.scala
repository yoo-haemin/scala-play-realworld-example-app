package core.users.services

import commons.models.{Email, Username}
import commons.repositories.DateTimeProvider
import commons.utils.DbioUtils
import core.authentication.api._
import core.users.exceptions.MissingUserException
import core.users.models.{FollowAssociation, FollowAssociationId, Profile, User}
import core.users.repositories.{FollowAssociationRepo, UserRepo}
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

private[users] class ProfileService(userRepo: UserRepo,
                                    followAssociationRepo: FollowAssociationRepo,
                                    securityUserProvider: SecurityUserProvider,
                                    securityUserUpdater: SecurityUserUpdater,
                                    dateTimeProvider: DateTimeProvider,
                                    userUpdateValidator: UserUpdateValidator,
                                    implicit private val ec: ExecutionContext) {

  def unfollow(followedUsername: Username, followerEmail: Email): DBIO[Profile] = {
    require(followedUsername != null && followerEmail != null)

    for {
      follower <- userRepo.byEmail(followerEmail)
      maybeFollowed <- userRepo.byUsername(followedUsername)
      followed <- DbioUtils.optionToDbio(maybeFollowed, new MissingUserException(followedUsername))
      _ <- deleteFollowAssociation(follower, followed)
    } yield Profile(followed, following = false)
  }

  private def deleteFollowAssociation(follower: User, followed: User) = {
    followAssociationRepo.byFollowerAndFollowed(follower.id, followed.id)
      .map(_.map(followAssociation => followAssociationRepo.delete(followAssociation.id)))
  }

  private def createFollowAssociation(follower: User, followed: User) = {
    followAssociationRepo.byFollowerAndFollowed(follower.id, followed.id)
      .map(_.getOrElse({
        val followAssociation = FollowAssociation(FollowAssociationId(-1), follower.id, followed.id)
        followAssociationRepo.insert(followAssociation)
      }))
  }

  def follow(followedUsername: Username, followerEmail: Email): DBIO[Profile] = {
    require(followedUsername != null && followerEmail != null)

    for {
      follower <- userRepo.byEmail(followerEmail)
      maybeFollowed <- userRepo.byUsername(followedUsername)
      followed <- DbioUtils.optionToDbio(maybeFollowed, new MissingUserException(followedUsername))
      _ <- createFollowAssociation(follower, followed)
    } yield Profile(followed, following = true)
  }

  def byUsername(username: Username, userContext: Option[Email]): DBIO[Profile] = {
    require(username != null && userContext != null)

    for {
      maybeUser <- userRepo.byUsername(username)
      user <- DbioUtils.optionToDbio(maybeUser, new MissingUserException(username))
      following <- isFollowing(user, userContext)
    } yield Profile(user, following)
  }

  private def isFollowing(followed: User, maybeFollowerEmail: Option[Email]) = maybeFollowerEmail match {
    case Some(email) =>
      for {
        follower <- userRepo.byEmail(email)
        maybeFollowAssociation <- followAssociationRepo.byFollowerAndFollowed(follower.id, followed.id)
      } yield maybeFollowAssociation.isDefined
    case None =>
      DBIO.successful(false)
  }

}