/*
 * Copyright (c) 2012-2018 Frederic Julian
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package org.decsync.flym

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.os.strictmode.UntaggedSocketViolation
import android.util.Log
import androidx.lifecycle.Observer
import kotlinx.coroutines.ObsoleteCoroutinesApi
import net.fred.feedex.BuildConfig
import org.decsync.flym.data.AppDatabase
import org.decsync.flym.data.entities.DecsyncArticle
import org.decsync.flym.data.entities.DecsyncCategory
import org.decsync.flym.data.entities.DecsyncFeed
import org.decsync.flym.data.utils.PrefConstants
import org.decsync.flym.utils.*
import org.decsync.library.Decsync
import org.decsync.library.DecsyncItem
import org.decsync.library.DecsyncObserver
import org.decsync.library.items.Rss
import java.util.concurrent.Executors


class App : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var context: Context
            private set

        @JvmStatic
        lateinit var db: AppDatabase
            private set

        @ExperimentalStdlibApi
        @ObsoleteCoroutinesApi
        private abstract class MyDecsyncObserver<T> : DecsyncObserver(), Observer<List<T>> {
            abstract fun toDecsyncItem(item: T): DecsyncItem

            override fun isDecsyncEnabled(): Boolean {
                return org.decsync.flym.App.Companion.context.getPrefBoolean(PrefConstants.DECSYNC_ENABLED, false)
            }

            override fun setEntries(entries: List<Decsync.EntryWithPath>) {
                DecsyncUtils.withDecsync(org.decsync.flym.App.Companion.context) { setEntries(entries) }
            }

            override fun executeStoredEntries(storedEntries: List<Decsync.StoredEntry>) {
                DecsyncUtils.withDecsync(org.decsync.flym.App.Companion.context) { executeStoredEntries(storedEntries, Extra()) }
            }

            override fun onChanged(newList: List<T>) {
                updateList(newList.map { toDecsyncItem(it) })
            }
        }

        @ExperimentalStdlibApi
        @ObsoleteCoroutinesApi
        private val articleObserver = object: org.decsync.flym.App.Companion.MyDecsyncObserver<DecsyncArticle>() {
            override fun toDecsyncItem(item: DecsyncArticle): Rss.Article = item.getRssArticle()
        }
        @ExperimentalStdlibApi
        @ObsoleteCoroutinesApi
        private val feedObserver = object: org.decsync.flym.App.Companion.MyDecsyncObserver<DecsyncFeed>() {
            override fun toDecsyncItem(item: DecsyncFeed): Rss.Feed = item.getRssFeed()
        }
        @ExperimentalStdlibApi
        @ObsoleteCoroutinesApi
        private val categoryObserver = object: org.decsync.flym.App.Companion.MyDecsyncObserver<DecsyncCategory>() {
            override fun toDecsyncItem(item: DecsyncCategory): Rss.Category = item.getRssCategory()
        }

        @ExperimentalStdlibApi
        @ObsoleteCoroutinesApi
        fun initSync() {
            org.decsync.flym.App.Companion.articleObserver.initSync()
            org.decsync.flym.App.Companion.feedObserver.initSync()
            org.decsync.flym.App.Companion.categoryObserver.initSync()
        }
    }

    @ExperimentalStdlibApi
    @ObsoleteCoroutinesApi
    override fun onCreate() {
        super.onCreate()

        org.decsync.flym.App.Companion.context = applicationContext
        org.decsync.flym.App.Companion.db = AppDatabase.createDatabase(org.decsync.flym.App.Companion.context)

        org.decsync.flym.App.Companion.context.putPrefBoolean(PrefConstants.IS_REFRESHING, false) // init

        // Enable strict mode to find performance issues in debug build
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build())
            val vmPolicy = VmPolicy.Builder().detectAll()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                vmPolicy.penaltyListener(Executors.newSingleThreadExecutor(), {
                    // Hide UntaggedSocketViolations since they are useless and unfixable in okhttp and glide
                    if (it !is UntaggedSocketViolation) {
                        Log.d("StrictMode", "StrictMode policy violation: " + it.stackTrace)
                    }
                })
            } else {
                vmPolicy.penaltyLog()
            }
            StrictMode.setVmPolicy(vmPolicy.build())
        }

        // Add DecSync observers
        org.decsync.flym.App.Companion.db.entryDao().observeAllDecsyncArticles.observeForever(org.decsync.flym.App.Companion.articleObserver)
        org.decsync.flym.App.Companion.db.feedDao().observeAllDecsyncFeeds.observeForever(org.decsync.flym.App.Companion.feedObserver)
        org.decsync.flym.App.Companion.db.feedDao().observeAllDecsyncCategories.observeForever(org.decsync.flym.App.Companion.categoryObserver)
    }
}