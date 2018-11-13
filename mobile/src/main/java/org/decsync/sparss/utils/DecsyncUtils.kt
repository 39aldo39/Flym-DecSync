/**
 * spaRSS DecSync
 * <p/>
 * Copyright (c) 2018 Aldo Gunsing
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.decsync.sparss.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import org.decsync.sparss.provider.FeedData
import org.decsync.sparss.provider.FeedDataContentProvider.addFeed
import org.decsync.sparss.service.DecsyncService
import org.decsync.library.*
import org.decsync.sparss.utils.DB.feedUrlToFeedId
import org.json.JSONObject
import kotlin.concurrent.thread

val dir = getDecsyncSubdir(null, "rss")
val ownAppId = getAppId("spaRSS")

object DecsyncUtils {
    private var mDecsync: Decsync<ContentResolver>? = null

    fun getDecsync(): Decsync<ContentResolver> {
        if (mDecsync == null) {
            val listeners = listOf(
                    ReadMarkListener(true),
                    ReadMarkListener(false),
                    SubscriptionsListener(),
                    FeedNamesListener(),
                    CategoriesListener(),
                    CategoryNamesListener(),
                    CategoryParentsListener()
            )
            mDecsync = Decsync(dir, ownAppId, listeners)
        }
        return mDecsync!!
    }

    fun initSync(context: Context) {
        thread {
            val decsync = DecsyncUtils.getDecsync()
            decsync.initStoredEntries()
            decsync.executeStoredEntries(listOf("feeds", "subscriptions"), context.contentResolver)
            context.startService(Intent(context, DecsyncService::class.java))
        }
    }

    class ReadMarkListener constructor(private val isReadEntry: Boolean) : OnSubdirEntryUpdateListener<ContentResolver> {
        override val subdir = listOf("articles", if (isReadEntry) "read" else "marked")

        override fun onSubdirEntryUpdate(path: List<String>, entry: Decsync.Entry, cr: ContentResolver) {
            Log.d(TAG, "Execute " + (if (isReadEntry) "read" else "mark") + " entry $entry")
            val entryColumn = if(isReadEntry) FeedData.EntryColumns.IS_READ else FeedData.EntryColumns.IS_FAVORITE
            val guid = entry.key
            if (guid !is String) {
                Log.w(TAG, "Invalid guid $guid")
                return
            }
            val value = entry.value
            if (value !is Boolean) {
                Log.w(TAG, "Invalid boolean $value")
                return
            }

            val values = ContentValues()
            if (value) {
                values.put(entryColumn, true)
            } else {
                values.putNull(entryColumn)
            }
            DB.update(cr, FeedData.EntryColumns.ALL_ENTRIES_CONTENT_URI, values,
                    FeedData.EntryColumns.GUID + "=?", arrayOf(guid), false)
        }
    }

    class SubscriptionsListener : OnSubfileEntryUpdateListener<ContentResolver> {
        override val subfile = listOf("feeds", "subscriptions")

        override fun onSubfileEntryUpdate(entry: Decsync.Entry, cr: ContentResolver) {
            Log.d(TAG, "Execute subscribe entry $entry")
            val feedUrl = entry.key
            if (feedUrl !is String) {
                Log.w(TAG, "Invalid feed $feedUrl")
                return
            }
            val subscribed = entry.value
            if (subscribed !is Boolean) {
                Log.w(TAG, "Invalid subscribed boolean $subscribed")
                return
            }

            if (subscribed) {
                addFeed(cr, null, feedUrl, "", false, false)
            } else {
                val feedId = feedUrlToFeedId(feedUrl, cr)
                if (feedId == null) {
                    Log.i(TAG, "Unknown feed $feedUrl")
                    return
                }
                val groupId = getGroupId(feedId, cr)
                if (groupId != null) {
                    removeGroupIfEmpty(groupId, cr)
                }
                DB.delete(cr, FeedData.FeedColumns.CONTENT_URI(feedId), null, null, false)
            }
        }
    }

    class FeedNamesListener : OnSubfileEntryUpdateListener<ContentResolver> {
        override val subfile = listOf("feeds", "names")

        override fun onSubfileEntryUpdate(entry: Decsync.Entry, cr: ContentResolver) {
            Log.d(TAG, "Execute rename entry $entry")
            val feedUrl = entry.key
            if (feedUrl !is String) {
                Log.w(TAG, "Invalid feed $feedUrl")
                return
            }
            val name = entry.value
            if (name !is String) {
                Log.w(TAG, "Invalid name $name")
                return
            }

            val feedId = feedUrlToFeedId(feedUrl, cr)
            if (feedId == null) {
                Log.i(TAG, "Unknown feed $feedUrl")
                return
            }
            val values = ContentValues()
            values.put(FeedData.FeedColumns.NAME, name)
            DB.update(cr, FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null, false)
        }
    }

    class CategoriesListener : OnSubfileEntryUpdateListener<ContentResolver> {
        override val subfile = listOf("feeds", "categories")

        override fun onSubfileEntryUpdate(entry: Decsync.Entry, cr: ContentResolver) {
            Log.d(TAG, "Execute move entry $entry")
            val feedUrl = entry.key
            if (feedUrl !is String) {
                Log.w(TAG, "Invalid feed $feedUrl")
                return
            }
            val categoryJson = entry.value
            val category = when (categoryJson) {
                JSONObject.NULL -> null
                is String -> categoryJson
                else -> {
                    Log.w(TAG, "Invalid category $categoryJson")
                    return
                }
            }

            val feedId = feedUrlToFeedId(feedUrl, cr)
            if (feedId == null) {
                Log.i(TAG, "Unknown feed $feedUrl")
                return
            }
            val oldGroupId = getGroupId(feedId, cr)
            val groupId = categoryToGroupId(category, cr)
            val values = ContentValues()
            values.put(FeedData.FeedColumns.GROUP_ID, groupId)
            DB.update(cr, FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null, false)
            removeGroupIfEmpty(oldGroupId, cr)
        }
    }

    class CategoryNamesListener : OnSubfileEntryUpdateListener<ContentResolver> {
        override val subfile = listOf("categories", "names")

        override fun onSubfileEntryUpdate(entry: Decsync.Entry, cr: ContentResolver) {
            Log.d(TAG, "Execute category rename entry $entry")
            val category = entry.key
            if (category !is String) {
                Log.w(TAG, "Invalid category $category")
                return
            }
            val name = entry.value
            if (name !is String) {
                Log.w(TAG, "Invalid name $name")
                return
            }

            val groupId = categoryToOptGroupId(category, cr)
            if (groupId == null) {
                Log.i(TAG, "Unknown category $category")
                return
            }
            val values = ContentValues()
            values.put(FeedData.FeedColumns.NAME, name)
            DB.update(cr, FeedData.FeedColumns.CONTENT_URI(groupId), values, null, null, false)
        }
    }

    class CategoryParentsListener : OnSubfileEntryUpdateListener<ContentResolver> {
        override val subfile = listOf("categories", "parents")

        override fun onSubfileEntryUpdate(entry: Decsync.Entry, cr: ContentResolver) {
            Log.i(TAG, "Nested folders are not supported in spaRSS")
        }
    }

    private fun getGroupId(feedId: String, cr: ContentResolver): String? {
        var groupId: String? = null
        cr.query(FeedData.FeedColumns.CONTENT_URI(feedId), arrayOf(FeedData.FeedColumns.GROUP_ID),
                null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                groupId = cursor.getString(0)
            }
        }
        return groupId
    }

    private fun categoryToOptGroupId(category: String?, cr: ContentResolver): String? {
        if (category == null) {
            return null
        }

        return cr.query(FeedData.FeedColumns.GROUPS_CONTENT_URI, arrayOf(FeedData.FeedColumns._ID),
                FeedData.FeedColumns.URL + "=?", arrayOf(category), null).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    private fun categoryToGroupId(category: String?, cr: ContentResolver): String? {
        if (category == null) {
            return null
        }

        val groupId = categoryToOptGroupId(category, cr)
        if (groupId != null) {
            return groupId
        }

        val values = ContentValues()
        values.put(FeedData.FeedColumns.IS_GROUP, 1)
        values.put(FeedData.FeedColumns.NAME, category)
        values.put(FeedData.FeedColumns.URL, category)
        val newGroupId = DB.insert(cr, FeedData.FeedColumns.GROUPS_CONTENT_URI, values, false).lastPathSegment
        getDecsync().executeStoredEntries(listOf("categories", "names"), cr, keyPred = { equalsJSON(it, category) })
        return newGroupId
    }

    private fun removeGroupIfEmpty(groupId: String?, cr: ContentResolver) {
        if (groupId == null) return
        cr.query(FeedData.FeedColumns.CONTENT_URI, FeedData.FeedColumns.PROJECTION_GROUP_ID,
                FeedData.FeedColumns.GROUP_ID + "=?", arrayOf(groupId), null).use { cursor ->
            if (!cursor.moveToFirst()) {
                DB.delete(cr, FeedData.FeedColumns.GROUPS_CONTENT_URI(groupId), null, null, false)
            }
        }
    }

    fun executePostSubscribeActions(feedUrl: String, cr: ContentResolver) {
        getDecsync().executeStoredEntries(listOf("feeds", "names"), cr, keyPred = { equalsJSON(it, feedUrl) })
        getDecsync().executeStoredEntries(listOf("feeds", "categories"), cr, keyPred = { equalsJSON(it, feedUrl) })
    }
}
