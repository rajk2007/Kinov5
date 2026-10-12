package com.lagradost.cloudstream3.ui.library

/** One-shot tab selection requested by an external intent, such as a notification. */
object LibraryTabManager {
    private var selectedTab: String = "downloading"

    fun setSelectedTab(tab: String) {
        selectedTab = tab
    }

    fun consumeSelectedTab(): String = selectedTab.also {
        selectedTab = "downloading"
    }
}
