package com.aware.app.ui

import com.aware.app.service.FeedItem

/** Display projection only: the complete classifier feed remains untouched. */
internal fun recentSurroundings(feed: List<FeedItem>): List<FeedItem> = feed
    .sortedByDescending { it.timeMs }
    .distinctBy { it.type to it.nameCalled }
    .take(5)
