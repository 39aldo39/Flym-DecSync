package org.decsync.flym.ui.discover

import android.view.View
import org.decsync.flym.data.entities.SearchFeedResult

interface FeedManagementInterface {

    fun searchForFeed(query: String)

    fun addFeed(view: View, title: String, link: String)

    fun deleteFeed(view: View, feed: SearchFeedResult)

    fun previewFeed(view: View, feed: SearchFeedResult)
}