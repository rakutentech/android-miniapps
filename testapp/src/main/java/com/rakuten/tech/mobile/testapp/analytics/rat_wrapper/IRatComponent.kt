package com.rakuten.tech.mobile.testapp.analytics.rat_wrapper

interface IRatComponent {
    /**
     * Prepare the component's rat event before it is sent using eventTypes, actions,
     * screen name, miniapp info
     */
    fun prepareEventToSend()

    /**
     * Set Custom rat events
     */
    fun setCustomRatEvent(ratEvent: RATEvent)

    /**
     * Clear Custom rat events
     */
    fun clearCustomRatEvent()

    /**
     * Get the screen name of the activity
     * @return the name of the screen to be reported to RAT for a screen view event
     * @throws UnsetScreenException if screen view is not set by overriding this method
     */
    @Throws(UnsetScreenException::class)
    fun getScreenName(): String

    /**
     * Set Custom screen name
     */
    fun setScreenName(screen_name: String)
}