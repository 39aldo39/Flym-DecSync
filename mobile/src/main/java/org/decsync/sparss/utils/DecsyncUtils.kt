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
import android.widget.Toast
import kotlinx.serialization.json.*
import org.decsync.library.Decsync
import org.decsync.library.DecsyncException
import org.decsync.library.getAppId
import org.decsync.library.getDefaultDecsyncDir
import org.decsync.sparss.provider.FeedData
import org.decsync.sparss.provider.FeedDataContentProvider.addFeed
import org.decsync.sparss.service.DecsyncService
import org.decsync.sparss.MainApplication
import org.decsync.sparss.utils.DB.feedUrlToFeedId
import kotlin.concurrent.thread

val ownAppId = getAppId("spaRSS")
const val TAG = "DecsyncUtils"

class Extra(val cr: ContentResolver)

@ExperimentalStdlibApi
object DecsyncUtils {
    private var mDecsync: Decsync<Extra>? = null

    private fun getNewDecsync(): Decsync<Extra>? {
        val decsyncDir = PrefUtils.getString(PrefUtils.DECSYNC_DIRECTORY, getDefaultDecsyncDir())
        try {
            val decsync = Decsync<Extra>(decsyncDir, "rss", null, ownAppId)
            decsync.addListener(listOf("articles", "read")) { path, entry, extra ->
                readMarkListener(true, path, entry, extra)
            }
            decsync.addListener(listOf("articles", "marked")) { path, entry, extra ->
                readMarkListener(false, path, entry, extra)
            }
            decsync.addListener(listOf("feeds", "subscriptions"), ::subscriptionsListener)
            decsync.addListener(listOf("feeds", "names"), ::feedNamesListener)
            decsync.addListener(listOf("feeds", "categories"), ::categoriesListener)
            decsync.addListener(listOf("categories", "names"), ::categoryNamesListener)
            decsync.addListener(listOf("categories", "parents"), ::categoryParentsListener)
            return decsync
        } catch (e: DecsyncException) {
            val context = MainApplication.getContext()
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            return null
        }
    }

    fun getDecsync(): Decsync<Extra>? {
        if (mDecsync == null && PrefUtils.getBoolean(PrefUtils.DECSYNC_ENABLED, false)) {
            mDecsync = getNewDecsync()
            if (mDecsync == null) {
                PrefUtils.putBoolean(PrefUtils.DECSYNC_ENABLED, false)
            }
        }
        return mDecsync
    }

    fun directoryChanged(context: Context) {
        context.stopService(Intent(context, DecsyncService::class.java))
        mDecsync = null
        initSync(context)
    }

    fun initSync(context: Context): Boolean {
        val decsync = getNewDecsync() ?: return false
        thread {
            decsync.initStoredEntries()
            val extra = Extra(context.contentResolver)
            decsync.executeStoredEntriesForPath(listOf("feeds", "subscriptions"), extra)
            context.startService(Intent(context, DecsyncService::class.java))
        }
        return true
    }

    private fun readMarkListener(isReadEntry: Boolean, path: List<String>, entry: Decsync.Entry, extra: Extra) {
        Log.d(TAG, "Execute " + (if (isReadEntry) "read" else "mark") + " entry $entry")
        val entryColumn = if (isReadEntry) FeedData.EntryColumns.IS_READ else FeedData.EntryColumns.IS_FAVORITE
        val guid = entry.key.content
        val value = entry.value.boolean

        val values = ContentValues()
        if (value) {
            values.put(entryColumn, true)
        } else {
            values.putNull(entryColumn)
        }
        DB.update(extra.cr, FeedData.EntryColumns.ALL_ENTRIES_CONTENT_URI, values,
                FeedData.EntryColumns.GUID + "=?", arrayOf(guid), false)
    }

    private fun subscriptionsListener(path: List<String>, entry: Decsync.Entry, extra: Extra) {
        Log.d(TAG, "Execute subscribe entry $entry")
        val feedUrl = entry.key.content
        val subscribed = entry.value.boolean

        if (subscribed) {
            addFeed(extra.cr, null, feedUrl, "", false, false)
        } else {
            val feedId = feedUrlToFeedId(feedUrl, extra.cr)
            if (feedId == null) {
                Log.i(TAG, "Unknown feed $feedUrl")
                return
            }
            val groupId = getGroupId(feedId, extra.cr)
            if (groupId != null) {
                removeGroupIfEmpty(groupId, extra.cr)
            }
            DB.delete(extra.cr, FeedData.FeedColumns.CONTENT_URI(feedId), null, null, false)
        }
    }

    private fun feedNamesListener(path: List<String>, entry: Decsync.Entry, extra: Extra) {
        Log.d(TAG, "Execute rename entry $entry")
        val feedUrl = entry.key.content
        val name = entry.value.content

        val feedId = feedUrlToFeedId(feedUrl, extra.cr)
        if (feedId == null) {
            Log.i(TAG, "Unknown feed $feedUrl")
            return
        }
        val values = ContentValues()
        values.put(FeedData.FeedColumns.NAME, name)
        DB.update(extra.cr, FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null, false)
    }

    private fun categoriesListener(path: List<String>, entry: Decsync.Entry, extra: Extra) {
        Log.d(TAG, "Execute move entry $entry")
        val feedUrl = entry.key.content
        val category = entry.value.contentOrNull

        val feedId = feedUrlToFeedId(feedUrl, extra.cr)
        if (feedId == null) {
            Log.i(TAG, "Unknown feed $feedUrl")
            return
        }
        val oldGroupId = getGroupId(feedId, extra.cr)
        val groupId = categoryToGroupId(category, extra.cr)
        val values = ContentValues()
        values.put(FeedData.FeedColumns.GROUP_ID, groupId)
        DB.update(extra.cr, FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null, false)
        removeGroupIfEmpty(oldGroupId, extra.cr)
    }

    private fun categoryNamesListener(path: List<String>, entry: Decsync.Entry, extra: Extra) {
        Log.d(TAG, "Execute category rename entry $entry")
        val category = entry.key.content
        val name = entry.value.content

        val groupId = categoryToOptGroupId(category, extra.cr)
        if (groupId == null) {
            Log.i(TAG, "Unknown category $category")
            return
        }
        val values = ContentValues()
        values.put(FeedData.FeedColumns.NAME, name)
        DB.update(extra.cr, FeedData.FeedColumns.CONTENT_URI(groupId), values, null, null, false)
    }

    private fun categoryParentsListener(path: List<String>, entry: Decsync.Entry, extra: Extra) {
        Log.i(TAG, "Nested folders are not supported in spaRSS")
    }

    private fun getGroupId(feedId: String, cr: ContentResolver): String? {
        var groupId: String? = null
        cr.query(FeedData.FeedColumns.CONTENT_URI(feedId), arrayOf(FeedData.FeedColumns.GROUP_ID),
                null, null, null)!!.use { cursor ->
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
                FeedData.FeedColumns.URL + "=?", arrayOf(category), null)!!.use { cursor ->
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
        val newGroupId = DB.insert(cr, FeedData.FeedColumns.GROUPS_CONTENT_URI, values, false)?.lastPathSegment ?: return null
        val extra = Extra(cr)
        getDecsync()?.executeStoredEntry(listOf("categories", "names"), JsonLiteral(category), extra)
        return newGroupId
    }

    private fun removeGroupIfEmpty(groupId: String?, cr: ContentResolver) {
        if (groupId == null) return
        cr.query(FeedData.FeedColumns.CONTENT_URI, FeedData.FeedColumns.PROJECTION_GROUP_ID,
                FeedData.FeedColumns.GROUP_ID + "=?", arrayOf(groupId), null)!!.use { cursor ->
            if (!cursor.moveToFirst()) {
                DB.delete(cr, FeedData.FeedColumns.GROUPS_CONTENT_URI(groupId), null, null, false)
            }
        }
    }

    fun executePostSubscribeActions(feedUrl: String, cr: ContentResolver) {
        val extra = Extra(cr)
        getDecsync()?.executeStoredEntry(listOf("feeds", "names"), JsonLiteral(feedUrl), extra)
        getDecsync()?.executeStoredEntry(listOf("feeds", "categories"), JsonLiteral(feedUrl), extra)
    }
}
