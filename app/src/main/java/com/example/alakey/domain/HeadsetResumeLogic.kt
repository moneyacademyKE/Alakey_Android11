package com.example.alakey.domain

/** Pure transition gate for connection events. A sticky initial event must not start audio. */
object HeadsetResumeLogic {
    fun shouldResume(previouslyConnected: Boolean?, currentlyConnected: Boolean): Boolean =
        previouslyConnected == false && currentlyConnected
}
