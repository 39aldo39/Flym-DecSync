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
import android.database.Cursor
import android.net.Uri
import org.decsync.library.Decsync
import org.decsync.sparss.provider.FeedData
import org.decsync.sparss.utils.DecsyncUtils.executePostSubscribeActions
import org.decsync.sparss.utils.DecsyncUtils.getDecsync
import org.json.JSONObject
import java.util.*

object DB {

    @JvmOverloads
    @JvmStatic
    fun insert(cr: ContentResolver, uri: Uri, values: ContentValues, updateDecsync: Boolean = true): Uri {
        val isGroup = values.getAsBoolean(FeedData.FeedColumns.IS_GROUP) ?: false
        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_ENABLED, false)) {
            when (cr.getType(uri)) {
                "vnd.android.cursor.item/vnd.spaRSS.feed", "vnd.android.cursor.dir/vnd.spaRSS.feed" -> {
                    if (!isGroup) {
                        if (updateDecsync) {
                            val feedUrl = values.getAsString(FeedData.FeedColumns.URL)
                            val name = values.getAsString(FeedData.FeedColumns.NAME)
                            val groupId = values.getAsString(FeedData.FeedColumns.GROUP_ID)
                            val category = groupToCategory(groupId, cr)

                            getDecsync()?.setEntry(listOf("feeds", "subscriptions"), feedUrl, true)
                            if (name != null) {
                                getDecsync()?.setEntry(listOf("feeds", "names"), feedUrl, name)
                            }
                            getDecsync()?.setEntry(listOf("feeds", "categories"), feedUrl, category)
                        }
                    }
                }
            }
        }

        val result = cr.insert(uri, values)
        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_ENABLED, false)) {
            when (cr.getType(uri)) {
                "vnd.android.cursor.item/vnd.spaRSS.feed", "vnd.android.cursor.dir/vnd.spaRSS.feed" -> {
                    if (!isGroup) {
                        executePostSubscribeActions(values.getAsString(FeedData.FeedColumns.URL), cr)
                    }
                }
            }
        }
        return result
    }

    @JvmOverloads
    @JvmStatic
    fun update(cr: ContentResolver, uri: Uri, values: ContentValues, selection: String?, selectionArgs: Array<String>?, updateDecsync: Boolean = true): Int {
        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_ENABLED, false)) {
            when (cr.getType(uri)) {
                "vnd.android.cursor.item/vnd.spaRSS.feed", "vnd.android.cursor.dir/vnd.spaRSS.feed" -> {
                    if (updateDecsync) {
                        cr.query(uri, null, selection, selectionArgs, null).use { cursor ->
                            val entries = mutableListOf<Decsync.EntryWithPath>()
                            cursor.moveToFirst()
                            while (!cursor.isAfterLast) {
                                val origUrl = cursor.getString(cursor.getColumnIndex(FeedData.FeedColumns.URL))
                                val origName = cursor.getString(cursor.getColumnIndex(FeedData.FeedColumns.NAME))
                                val origGroupId = cursor.getString(cursor.getColumnIndex(FeedData.FeedColumns.GROUP_ID))
                                val isGroup = cursor.getInt(cursor.getColumnIndex(FeedData.FeedColumns.IS_GROUP)) == 1
                                val url = if (values.containsKey(FeedData.FeedColumns.URL)) values.getAsString(FeedData.FeedColumns.URL) else origUrl
                                val name = if (values.containsKey(FeedData.FeedColumns.NAME)) values.getAsString(FeedData.FeedColumns.NAME) else origName
                                val groupId = if (values.containsKey(FeedData.FeedColumns.GROUP_ID)) values.getAsString(FeedData.FeedColumns.GROUP_ID) else origGroupId
                                var newUrl = false
                                if (isGroup) {
                                    if (origName != name) {
                                        entries.add(Decsync.EntryWithPath(listOf("categories", "names"), url, name))
                                    }
                                } else {
                                    if (origUrl != null && origUrl != url) {
                                        entries.add(Decsync.EntryWithPath(listOf("feeds", "subscriptions"), origUrl, false))
                                        entries.add(Decsync.EntryWithPath(listOf("feeds", "subscriptions"), url, true))
                                        newUrl = true
                                    }
                                    if (newUrl || origName != name) {
                                        entries.add(Decsync.EntryWithPath(listOf("feeds", "names"), url, name))
                                    }
                                    if (newUrl || origGroupId != null && origGroupId != groupId || origGroupId == null && groupId != null) {
                                        val category = groupToCategory(groupId, cr)
                                        entries.add(Decsync.EntryWithPath(listOf("feeds", "categories"), url, category))
                                    }
                                }

                                cursor.moveToNext()
                            }
                            getDecsync()?.setEntries(entries)
                        }
                    }
                }
                "vnd.android.cursor.item/vnd.spaRSS.entry", "vnd.android.cursor.dir/vnd.spaRSS.entry" -> {
                    if (updateDecsync) {
                        if (values.containsKey(FeedData.EntryColumns.IS_READ)) {
                            cr.query(uri, null, selection, selectionArgs, null).use { cursor ->
                                val read = java.lang.Boolean.TRUE == values.getAsBoolean(FeedData.EntryColumns.IS_READ)
                                setReadOrMarkEntries("read", cursor, read)
                            }
                        }
                        if (values.containsKey(FeedData.EntryColumns.IS_FAVORITE)) {
                            cr.query(uri, null, selection, selectionArgs, null).use { cursor ->
                                val marked = java.lang.Boolean.TRUE == values.getAsBoolean(FeedData.EntryColumns.IS_FAVORITE)
                                setReadOrMarkEntries("marked", cursor, marked)
                            }
                        }
                    }
                }
            }
        }

        return cr.update(uri, values, selection, selectionArgs)
    }

    private fun setReadOrMarkEntries(type: String, cursor: Cursor, value: Boolean) {
        val entries = mutableListOf<Decsync.EntryWithPath>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val date = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            date.timeInMillis = cursor.getLong(cursor.getColumnIndex(FeedData.EntryColumns.DATE))
            val year = "%04d".format(date.get(Calendar.YEAR))
            val month = "%02d".format(date.get(Calendar.MONTH) + 1)
            val day = "%02d".format(date.get(Calendar.DAY_OF_MONTH))
            val path = listOf("articles", type, year, month, day)
            val guid = cursor.getString(cursor.getColumnIndex(FeedData.EntryColumns.GUID))
            entries.add(Decsync.EntryWithPath(path, guid, value))

            cursor.moveToNext()
        }
        getDecsync()?.setEntries(entries)
    }

    @JvmOverloads
    @JvmStatic
    fun delete(cr: ContentResolver, uri: Uri, selection: String?, selectionArgs: Array<String>?, updateDecsync: Boolean = true): Int {
        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_ENABLED, false)) {
            when (cr.getType(uri)) {
                "vnd.android.cursor.item/vnd.spaRSS.feed", "vnd.android.cursor.dir/vnd.spaRSS.feed" -> {
                    if (updateDecsync) {
                        cr.query(uri, null, selection, selectionArgs, null).use {
                            setUnsubscribeEntries(cr, it)
                        }
                    }
                }
            }
        }

        return cr.delete(uri, selection, selectionArgs)
    }

    private fun setUnsubscribeEntries(cr: ContentResolver, cursor: Cursor) {
        val entries = mutableListOf<Decsync.Entry>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            if (cursor.getInt(cursor.getColumnIndex(FeedData.FeedColumns.IS_GROUP)) == 1) {
                val groupId = cursor.getString(cursor.getColumnIndex(FeedData.FeedColumns._ID))
                cr.query(FeedData.FeedColumns.CONTENT_URI, null,
                        FeedData.FeedColumns.GROUP_ID + "=?", arrayOf(groupId), null).use {
                    setUnsubscribeEntries(cr, it)
                }
            } else {
                val feedUrl = cursor.getString(cursor.getColumnIndex(FeedData.FeedColumns.URL))
                entries.add(Decsync.Entry(feedUrl, false))
            }

            cursor.moveToNext()
        }
        DecsyncUtils.getDecsync()?.setEntriesForPath(listOf("feeds", "subscriptions"), entries)
    }

    private fun groupToCategory(groupId: String?, cr: ContentResolver): Any {
        if (groupId == null) {
            return JSONObject.NULL
        }
        var categoryVar = JSONObject.NULL
        cr.query(FeedData.FeedColumns.GROUPS_CONTENT_URI(groupId),
                arrayOf(FeedData.FeedColumns.URL, FeedData.FeedColumns.NAME),
                null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                categoryVar = cursor.getString(0)
                val name = cursor.getString(1)
                if (categoryVar == null) {
                    val category = "catID%05d".format(Random().nextInt(100000))
                    categoryVar = category
                    val values = ContentValues()
                    values.put(FeedData.FeedColumns.URL, category)
                    cr.update(FeedData.FeedColumns.CONTENT_URI(groupId), values, null, null)
                    getDecsync()?.setEntry(listOf("categories", "names"), category, name)
                }
            }
        }
        return categoryVar
    }

    fun feedUrlToFeedId(feedUrl: String?, cr: ContentResolver): String? {
        if (feedUrl == null || feedUrl.isEmpty()) return null
        var feedId: String? = null
        cr.query(FeedData.FeedColumns.CONTENT_URI, FeedData.FeedColumns.PROJECTION_ID,
                FeedData.FeedColumns.URL + "=?", arrayOf(feedUrl), null).use { cursor ->
            if (cursor.moveToFirst()) {
                feedId = java.lang.Long.toString(cursor.getLong(0))
            }
        }
        return feedId
    }
}
