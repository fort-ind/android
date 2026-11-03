package com.fortind.zenith.people

enum class FollowState {
    Hidden,
    Follow,
    FollowBack,
    RequestToFollow,
    Following,
    Requested;

    val invitesFollow: Boolean get() = this == Follow || this == FollowBack || this == RequestToFollow

    val asksFirst: Boolean get() = this == Following || this == Requested
}

fun UserDetail.followState(myUserId: String?): FollowState = when {
    id == myUserId || isSuspended || hasMoved -> FollowState.Hidden
    isFollowing -> FollowState.Following
    hasPendingRequestFromYou -> FollowState.Requested
    isLocked -> FollowState.RequestToFollow
    isFollowed -> FollowState.FollowBack
    else -> FollowState.Follow
}

fun UserDetail.afterToggle(state: FollowState): UserDetail = when (state) {
    FollowState.Hidden -> this
    FollowState.Following -> copy(isFollowing = false)
    FollowState.Requested -> copy(hasPendingRequestFromYou = false)
    FollowState.RequestToFollow -> copy(hasPendingRequestFromYou = true)
    FollowState.Follow, FollowState.FollowBack -> copy(isFollowing = true)
}
