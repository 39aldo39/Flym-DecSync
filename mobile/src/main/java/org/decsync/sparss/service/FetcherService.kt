/**
 * spaRSS
 *
 *
 * Copyright (c) 2015-2016 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 *
 *
 *
 *
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 *
 *
 * Copyright (c) 2010-2012 Stefan Handschuh
 *
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.decsync.sparss.service

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.util.Xml
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.decsync.sparss.Constants
import org.decsync.sparss.MainApplication
import org.decsync.sparss.R
import org.decsync.sparss.activity.HomeActivity
import org.decsync.sparss.parser.RssAtomParser
import org.decsync.sparss.provider.FeedData.*
import org.decsync.sparss.utils.*
import org.decsync.sparss.utils.DB.update
import org.decsync.sparss.utils.DecsyncUtils.getDecsync
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.regex.Pattern

@ExperimentalStdlibApi
class FetcherService : IntentService(FetcherService::class.java.simpleName) {

    private val mHandler: Handler

    init {
        HttpURLConnection.setFollowRedirects(true)
        mHandler = Handler()
    }

    public override fun onHandleIntent(intent: Intent?) {
        if (intent == null) { // No intent, we quit
            return
        }

        val isFromAutoRefresh = intent.getBooleanExtra(Constants.FROM_AUTO_REFRESH, false)

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        // Connectivity issue, we quit
        if (networkInfo == null || networkInfo.state != NetworkInfo.State.CONNECTED) {
            if (ACTION_REFRESH_FEEDS == intent.action && !isFromAutoRefresh) {
                // Display a toast in that case
                mHandler.post {
                    Toast.makeText(this@FetcherService, R.string.network_error, Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val skipFetch = (isFromAutoRefresh && PrefUtils.getBoolean(PrefUtils.REFRESH_WIFI_ONLY, false)
                && networkInfo.type != ConnectivityManager.TYPE_WIFI)
        // We need to skip the fetching process, so we quit
        if (skipFetch) {
            return
        }

        if (ACTION_MOBILIZE_FEEDS == intent.action) {
            mobilizeAllEntries()
            downloadAllImages()
        } else if (ACTION_DOWNLOAD_IMAGES == intent.action) {
            downloadAllImages()
        } else { // == Constants.ACTION_REFRESH_FEEDS
            PrefUtils.checkAppUpgrade()
            if (PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false) ||
                    !PrefUtils.getBoolean(PrefUtils.INTRO_DONE, false) ||
                    PrefUtils.getBoolean(PrefUtils.UPDATE_FORCES_SAF, false)) {
                return
            }
            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, true)

            if (isFromAutoRefresh) {
                PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, SystemClock.elapsedRealtime())
            }

            val keepTime = PrefUtils.getString(PrefUtils.KEEP_TIME, "4").toLong() * 86400000L
            val keepDateBorderTime = if (keepTime > 0) System.currentTimeMillis() - keepTime else 0

            deleteOldEntries(keepDateBorderTime)

            val feedId = intent.getStringExtra(Constants.FEED_ID)
            var newCount = feedId?.let { refreshFeed(it, keepDateBorderTime) }
                    ?: refreshFeeds(keepDateBorderTime)

            if (newCount > 0) {
                if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_ENABLED, true)) {
                    val cursor = contentResolver.query(EntryColumns.CONTENT_URI, arrayOf(Constants.DB_COUNT), EntryColumns.WHERE_UNREAD, null, null)

                    cursor!!.moveToFirst()
                    newCount = cursor.getInt(0) // The number has possibly changed
                    cursor.close()

                    if (newCount > 0) {
                        if (Build.VERSION.SDK_INT >= 26 && Constants.NOTIF_MGR != null) {
                            val name = getString(R.string.channel_new_entries_name)
                            val descriptionText = getString(R.string.channel_new_entries_description)
                            val importance = NotificationManager.IMPORTANCE_LOW
                            val channel = NotificationChannel(CHANNEL_NEW_ENTRIES, name, importance)
                            channel.description = descriptionText
                            Constants.NOTIF_MGR.createNotificationChannel(channel)
                        }

                        val text = resources.getQuantityString(R.plurals.number_of_new_entries, newCount, newCount)

                        val notificationIntent = Intent(this@FetcherService, HomeActivity::class.java)
                        val contentIntent = PendingIntent.getActivity(this@FetcherService, 0, notificationIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT)

                        val notifBuilder = NotificationCompat.Builder(MainApplication.getContext(), CHANNEL_NEW_ENTRIES) //
                                .setContentIntent(contentIntent) //
                                .setSmallIcon(R.drawable.ic_statusbar_rss) //
                                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)) //
                                .setTicker(text) //
                                .setWhen(System.currentTimeMillis()) //
                                .setAutoCancel(true) //
                                .setContentTitle(getString(R.string.spaRSS_feeds)) //
                                .setContentText(text) //
                                .setLights(-0x1, 0, 0)

                        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_VIBRATE, false)) {
                            notifBuilder.setVibrate(longArrayOf(0, 1000))
                        }

                        val ringtone = PrefUtils.getString(PrefUtils.NOTIFICATIONS_RINGTONE, null)
                        if (ringtone != null && ringtone.isNotEmpty()) {
                            notifBuilder.setSound(Uri.parse(ringtone))
                        }

                        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_LIGHT, false)) {
                            notifBuilder.setLights(-0x1, 300, 1000)
                        }

                        Constants.NOTIF_MGR?.notify(0, notifBuilder.build())
                    }
                } else if (Constants.NOTIF_MGR != null) {
                    Constants.NOTIF_MGR.cancel(0)
                }
            }

            if (PrefUtils.getBoolean(PrefUtils.DECSYNC_ENABLED, false)) {
                val extra = Extra(this)
                getDecsync(this)?.executeAllNewEntries(extra, false)
            }
            mobilizeAllEntries()
            downloadAllImages()

            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, false)
        }
    }

    private fun mobilizeAllEntries() {
        val cr = contentResolver
        val cursor = cr.query(TaskColumns.CONTENT_URI, arrayOf(TaskColumns._ID, TaskColumns.ENTRY_ID, TaskColumns.NUMBER_ATTEMPT),
                TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, null, null)

        val operations = ArrayList<ContentProviderOperation>()

        while (cursor!!.moveToNext()) {
            val taskId = cursor.getLong(0)
            val entryId = cursor.getLong(1)
            var nbAttempt = 0
            if (!cursor.isNull(2)) {
                nbAttempt = cursor.getInt(2)
            }

            var success = false

            val entryUri = EntryColumns.CONTENT_URI(entryId)
            val entryCursor = cr.query(entryUri, null, null, null, null)

            if (entryCursor!!.moveToFirst()) {
                if (entryCursor.isNull(entryCursor.getColumnIndex(EntryColumns.MOBILIZED_HTML))) { // If we didn't already mobilized it
                    val linkPos = entryCursor.getColumnIndex(EntryColumns.LINK)
                    val abstractHtmlPos = entryCursor.getColumnIndex(EntryColumns.ABSTRACT)
                    val feedIdPos = entryCursor.getColumnIndex(EntryColumns.FEED_ID)
                    var connection: HttpURLConnection? = null

                    try {
                        val link = entryCursor.getString(linkPos)
                        val feedId = entryCursor.getString(feedIdPos)
                        val cursorFeed = cr.query(FeedColumns.CONTENT_URI(feedId), null, null, null, null)
                        cursorFeed!!.moveToNext()
                        val cookieNamePosition = cursorFeed.getColumnIndex(FeedColumns.COOKIE_NAME)
                        val cookieValuePosition = cursorFeed.getColumnIndex(FeedColumns.COOKIE_VALUE)
                        val cookieName = cursorFeed.getString(cookieNamePosition)
                        val cookieValue = cursorFeed.getString(cookieValuePosition)
                        val httpAuthLoginPosition = cursorFeed.getColumnIndex(FeedColumns.HTTP_AUTH_LOGIN)
                        val httpAuthPasswordPosition = cursorFeed.getColumnIndex(FeedColumns.HTTP_AUTH_PASSWORD)
                        val httpAuthLoginValue = cursorFeed.getString(httpAuthLoginPosition)
                        val httpAuthPassValue = cursorFeed.getString(httpAuthPasswordPosition)
                        cursorFeed.close()

                        // Try to find a text indicator for better content extraction
                        var contentIndicator: String? = null
                        var text = entryCursor.getString(abstractHtmlPos)
                        if (!TextUtils.isEmpty(text)) {
                            text = Html.fromHtml(text).toString()
                            if (text.length > 60) {
                                contentIndicator = text.substring(20, 40)
                            }
                        }

                        connection = NetworkUtils.setupConnection(link, cookieName, cookieValue, httpAuthLoginValue, httpAuthPassValue)

                        var mobilizedHtml = ArticleTextExtractor.extractContent(connection.inputStream, contentIndicator)

                        if (mobilizedHtml != null) {
                            mobilizedHtml = HtmlUtils.improveHtmlContent(mobilizedHtml, NetworkUtils.getBaseUrl(link))
                            val values = ContentValues()
                            values.put(EntryColumns.MOBILIZED_HTML, mobilizedHtml)

                            var imgUrlsToDownload: ArrayList<String>? = null
                            if (NetworkUtils.needDownloadPictures()) {
                                imgUrlsToDownload = HtmlUtils.getImageURLs(mobilizedHtml)
                            }

                            var mainImgUrl: String?
                            mainImgUrl = if (imgUrlsToDownload != null) {
                                HtmlUtils.getMainImageURL(imgUrlsToDownload)
                            } else {
                                HtmlUtils.getMainImageURL(mobilizedHtml)
                            }

                            if (mainImgUrl != null) {
                                values.put(EntryColumns.IMAGE_URL, mainImgUrl)
                            }

                            if (update(this, entryUri, values, null, null) > 0) {
                                success = true
                                operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build())
                                if (imgUrlsToDownload != null && imgUrlsToDownload.isNotEmpty()) {
                                    addImagesToDownload(entryId.toString(), imgUrlsToDownload)
                                }
                            }
                        }
                    } catch (ignored: Throwable) {
                    } finally {
                        connection?.disconnect()
                    }
                } else { // We already mobilized it
                    success = true
                    operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build())
                }
            }
            entryCursor.close()

            if (!success) {
                if (nbAttempt + 1 > MAX_TASK_ATTEMPT) {
                    operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build())
                } else {
                    val values = ContentValues()
                    values.put(TaskColumns.NUMBER_ATTEMPT, nbAttempt + 1)
                    operations.add(ContentProviderOperation.newUpdate(TaskColumns.CONTENT_URI(taskId)).withValues(values).build())
                }
            }
        }

        cursor.close()

        if (operations.isNotEmpty()) {
            try {
                cr.applyBatch(AUTHORITY, operations)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun downloadAllImages() {
        val cr = MainApplication.getContext().contentResolver
        val cursor = cr.query(TaskColumns.CONTENT_URI, arrayOf(TaskColumns._ID, TaskColumns.ENTRY_ID, TaskColumns.IMG_URL_TO_DL,
                TaskColumns.NUMBER_ATTEMPT), TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NOT_NULL, null, null)

        val operations = ArrayList<ContentProviderOperation>()

        while (cursor!!.moveToNext()) {
            val taskId = cursor.getLong(0)
            val entryId = cursor.getLong(1)
            val imgPath = cursor.getString(2)
            var nbAttempt = 0
            if (!cursor.isNull(3)) {
                nbAttempt = cursor.getInt(3)
            }

            try {
                NetworkUtils.downloadImage(entryId, imgPath)

                // If we are here, everything is OK
                operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build())
            } catch (e: Exception) {
                if (nbAttempt + 1 > MAX_TASK_ATTEMPT) {
                    operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build())
                } else {
                    val values = ContentValues()
                    values.put(TaskColumns.NUMBER_ATTEMPT, nbAttempt + 1)
                    operations.add(ContentProviderOperation.newUpdate(TaskColumns.CONTENT_URI(taskId)).withValues(values).build())
                }
            }
        }

        cursor.close()

        if (operations.isNotEmpty()) {
            try {
                cr.applyBatch(AUTHORITY, operations)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun deleteOldEntries(keepDateBorderTime: Long) {
        if (keepDateBorderTime > 0) {
            val where = EntryColumns.DATE + '<' + keepDateBorderTime + Constants.DB_AND + EntryColumns.WHERE_NOT_FAVORITE
            // Delete the entries
            MainApplication.getContext().contentResolver.delete(EntryColumns.CONTENT_URI, where, null)
            // Delete the cache files
            NetworkUtils.deleteEntriesImagesCache(keepDateBorderTime)
        }
        val cursor = MainApplication.getContext().contentResolver.query(FeedColumns.CONTENT_URI, arrayOf(FeedColumns._ID, FeedColumns.KEEP_TIME), null, null, null)
        while (cursor!!.moveToNext()) {
            val feedid = cursor.getLong(0)
            val keepTimeLocal = cursor.getLong(1) * 86400000L
            val keepDateBorderTimeLocal = if (keepTimeLocal > 0) System.currentTimeMillis() - keepTimeLocal else 0
            if (keepDateBorderTimeLocal > 0) {
                val where = EntryColumns.DATE + '<' + keepDateBorderTimeLocal + Constants.DB_AND + EntryColumns.WHERE_NOT_FAVORITE + Constants.DB_AND + EntryColumns.FEED_ID + "=" + feedid.toString()
                MainApplication.getContext().contentResolver.delete(EntryColumns.CONTENT_URI, where, null)
            }
        }
        cursor.close()
    }

    private fun refreshFeeds(keepDateBorderTime: Long): Int {
        val cr = contentResolver
        val cursor = cr.query(FeedColumns.CONTENT_URI, FeedColumns.PROJECTION_ID, null, null, null)
        val nbFeed = cursor!!.count

        val executor = Executors.newFixedThreadPool(THREAD_NUMBER) { r ->
            val t = Thread(r)
            t.priority = Thread.MIN_PRIORITY
            t
        }

        val completionService: CompletionService<Int> = ExecutorCompletionService(executor)
        while (cursor.moveToNext()) {
            val feedId = cursor.getString(0)
            completionService.submit {
                var result = 0
                try {
                    result = refreshFeed(feedId, keepDateBorderTime)
                } catch (ignored: Exception) {
                }
                result
            }
        }
        cursor.close()

        var globalResult = 0
        for (i in 0 until nbFeed) {
            try {
                val f = completionService.take()
                globalResult += f.get()
            } catch (ignored: Exception) {
            }
        }

        executor.shutdownNow() // To purge all threads

        return globalResult
    }

    private fun refreshFeed(feedId: String, keepDateBorderTime: Long): Int {
        var handler: RssAtomParser? = null

        val cr = contentResolver
        val cursor = cr.query(FeedColumns.CONTENT_URI(feedId), null, null, null, null)

        if (cursor!!.moveToFirst()) {
            val urlPosition = cursor.getColumnIndex(FeedColumns.URL)
            val idPosition = cursor.getColumnIndex(FeedColumns._ID)
            val titlePosition = cursor.getColumnIndex(FeedColumns.NAME)
            val fetchModePosition = cursor.getColumnIndex(FeedColumns.FETCH_MODE)
            val realLastUpdatePosition = cursor.getColumnIndex(FeedColumns.REAL_LAST_UPDATE)
            val iconPosition = cursor.getColumnIndex(FeedColumns.ICON)
            val retrieveFullscreenPosition = cursor.getColumnIndex(FeedColumns.RETRIEVE_FULLTEXT)
            val httpAuthLoginPosition = cursor.getColumnIndex(FeedColumns.HTTP_AUTH_LOGIN)
            val httpAuthPasswordPosition = cursor.getColumnIndex(FeedColumns.HTTP_AUTH_PASSWORD)
            val httpAuthLoginValue = cursor.getString(httpAuthLoginPosition)
            val httpAuthPassValue = cursor.getString(httpAuthPasswordPosition)

            val id = cursor.getString(idPosition)

            var connection: HttpURLConnection? = null

            try {
                val feedUrl = cursor.getString(urlPosition)
                connection = NetworkUtils.setupConnection(feedUrl, httpAuthLoginValue, httpAuthPassValue)
                var contentType = connection.contentType
                var fetchMode = cursor.getInt(fetchModePosition)

                handler = RssAtomParser(Date(cursor.getLong(realLastUpdatePosition)), keepDateBorderTime, id, cursor.getString(titlePosition), feedUrl,
                        cursor.getInt(retrieveFullscreenPosition) == 1)
                handler.setFetchImages(NetworkUtils.needDownloadPictures())

                if (fetchMode == 0) {
                    if (contentType != null && contentType.startsWith(CONTENT_TYPE_TEXT_HTML)) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))

                        var line: String
                        var posStart = -1

                        while (reader.readLine().also { line = it } != null) {
                            if (line.contains(HTML_BODY)) {
                                break
                            } else {
                                val matcher = FEED_LINK_PATTERN.matcher(line)

                                if (matcher.find()) { // not "while" as only one link is needed
                                    line = matcher.group()
                                    posStart = line.indexOf(HREF)

                                    if (posStart > -1) {
                                        var url = line.substring(posStart + 6, line.indexOf('"', posStart + 10)).replace(Constants.AMP_SG,
                                                Constants.AMP)

                                        val values = ContentValues()

                                        if (url.startsWith(Constants.SLASH)) {
                                            val index = feedUrl.indexOf('/', 8)

                                            url = if (index > -1) {
                                                feedUrl.substring(0, index) + url
                                            } else {
                                                feedUrl + url
                                            }
                                        } else if (!url.startsWith(Constants.HTTP_SCHEME) && !url.startsWith(Constants.HTTPS_SCHEME)) {
                                            url = "$feedUrl/$url"
                                        }
                                        values.put(FeedColumns.URL, url)
                                        update(this, FeedColumns.CONTENT_URI(id), values, null, null)
                                        connection!!.disconnect()
                                        connection = NetworkUtils.setupConnection(URL(url))
                                        contentType = connection.contentType
                                        break
                                    }
                                }
                            }
                        }
                        // this indicates a badly configured feed
                        if (posStart == -1) {
                            connection!!.disconnect()
                            connection = NetworkUtils.setupConnection(URL(feedUrl))
                            contentType = connection.contentType
                        }
                    }

                    if (contentType != null) {
                        val index = contentType.indexOf(CHARSET)
                        fetchMode = if (index > -1) {
                            val index2 = contentType.indexOf(';', index)

                            try {
                                Xml.findEncodingByName(if (index2 > -1) contentType.substring(index + 8, index2) else contentType.substring(index + 8))
                                FETCHMODE_DIRECT
                            } catch (ignored: UnsupportedEncodingException) {
                                FETCHMODE_REENCODE
                            }
                        } else {
                            FETCHMODE_REENCODE
                        }

                    } else {
                        val bufferedReader = BufferedReader(InputStreamReader(connection!!.inputStream))

                        val chars = CharArray(20)

                        val length = bufferedReader.read(chars)

                        val xmlDescription = String(chars, 0, length)

                        connection.disconnect()
                        connection = NetworkUtils.setupConnection(connection.url)

                        val start = xmlDescription.indexOf(ENCODING)

                        fetchMode = if (start > -1) {
                            try {
                                Xml.findEncodingByName(xmlDescription.substring(start + 10, xmlDescription.indexOf('"', start + 11)))
                                FETCHMODE_DIRECT
                            } catch (ignored: UnsupportedEncodingException) {
                                FETCHMODE_REENCODE
                            }
                        } else {
                            // absolutely no encoding information found
                            FETCHMODE_DIRECT
                        }
                    }

                    val values = ContentValues()
                    values.put(FeedColumns.FETCH_MODE, fetchMode)
                    update(this, FeedColumns.CONTENT_URI(id), values, null, null)
                }

                when (fetchMode) {
                    FETCHMODE_REENCODE -> {
                        val outputStream = ByteArrayOutputStream()
                        val inputStream = connection!!.inputStream

                        val byteBuffer = ByteArray(4096)

                        var n: Int
                        while (inputStream.read(byteBuffer).also { n = it } > 0) {
                            outputStream.write(byteBuffer, 0, n)
                        }

                        val xmlText = outputStream.toString()

                        val start = xmlText.indexOf(ENCODING)

                        if (start > -1) {
                            Xml.parse(
                                    StringReader(String(outputStream.toByteArray(),
                                            Charset.forName(xmlText.substring(start + 10, xmlText.indexOf('"', start + 11))))),
                                    handler
                            )
                        } else {
                            // use content type
                            if (contentType != null) {
                                val index = contentType.indexOf(CHARSET)
                                if (index > -1) {
                                    val index2 = contentType.indexOf(';', index)
                                    try {
                                        val reader = StringReader(String(outputStream.toByteArray(),
                                                Charset.forName(if (index2 > -1) contentType.substring(index + 8, index2) else contentType.substring(index + 8))))
                                        Xml.parse(reader, handler)
                                    } catch (ignored: Exception) {
                                    }
                                } else {
                                    val reader = StringReader(xmlText)
                                    Xml.parse(reader, handler)
                                }
                            }
                        }
                    }
                    //FETCHMODE_DIRECT
                    else -> {
                        if (contentType != null) {
                            val index = contentType.indexOf(CHARSET)
                            val index2 = contentType.indexOf(';', index)

                            val inputStream = connection!!.inputStream
                            Xml.parse(inputStream,
                                    Xml.findEncodingByName(if (index2 > -1) contentType.substring(index + 8, index2) else contentType.substring(index + 8)),
                                    handler)
                        } else {
                            val reader = InputStreamReader(connection!!.inputStream)
                            Xml.parse(reader, handler)
                        }
                    }
                }

                connection.disconnect()
            } catch (e: FileNotFoundException) {
                if (handler == null || !handler.isDone && !handler.isCancelled) {
                    val values = ContentValues()

                    // resets the fetch mode to determine it again later
                    values.put(FeedColumns.FETCH_MODE, 0)

                    values.put(FeedColumns.ERROR, getString(R.string.error_feed_error))
                    update(this, FeedColumns.CONTENT_URI(id), values, null, null)
                }
            } catch (e: Throwable) {
                if (handler == null || !handler.isDone && !handler.isCancelled) {
                    val values = ContentValues()

                    // resets the fetch mode to determine it again later
                    values.put(FeedColumns.FETCH_MODE, 0)

                    values.put(FeedColumns.ERROR, if (e.message != null) e.message else getString(R.string.error_feed_process))
                    Log.w("spaRSS", "The feed can not be processed", e)
                    update(this, FeedColumns.CONTENT_URI(id), values, null, null)
                }
            } finally {
                /* check and optionally find favicon */
                try {
                    if (handler != null && cursor.getBlob(iconPosition) == null) {
                        val feedLink = handler.feedLink
                        if (feedLink != null) {
                            NetworkUtils.retrieveFavicon(this, URL(feedLink), id)
                        } else {
                            NetworkUtils.retrieveFavicon(this, connection!!.url, id)
                        }
                    }
                } catch (ignored: Throwable) {
                }

                connection?.disconnect()
            }
        }

        cursor.close()

        return handler?.newCount ?: 0
    }

    companion object {
        const val ACTION_REFRESH_FEEDS = "org.decsync.sparss.REFRESH"
        const val ACTION_MOBILIZE_FEEDS = "org.decsync.sparss.MOBILIZE_FEEDS"
        const val ACTION_DOWNLOAD_IMAGES = "org.decsync.sparss.DOWNLOAD_IMAGES"

        private const val THREAD_NUMBER = 3
        private const val MAX_TASK_ATTEMPT = 3

        private const val FETCHMODE_DIRECT = 1
        private const val FETCHMODE_REENCODE = 2

        private const val CHARSET = "charset="
        private const val CONTENT_TYPE_TEXT_HTML = "text/html"
        private const val HREF = "href=\""

        private const val HTML_BODY = "<body"
        private const val ENCODING = "encoding=\""

        /* Allow different positions of the "rel" attribute w.r.t. the "href" attribute */
        private val FEED_LINK_PATTERN = Pattern.compile(
                "[.]*<link[^>]* ((rel=alternate|rel=\"alternate\")[^>]* href=\"[^\"]*\"|href=\"[^\"]*\"[^>]* (rel=alternate|rel=\"alternate\"))[^>]*>",
                Pattern.CASE_INSENSITIVE)

        private const val CHANNEL_NEW_ENTRIES = "new_entries"

        fun hasMobilizationTask(entryId: Long): Boolean {
            val cursor = MainApplication.getContext().contentResolver.query(TaskColumns.CONTENT_URI, TaskColumns.PROJECTION_ID,
                    TaskColumns.ENTRY_ID + '=' + entryId + Constants.DB_AND + TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, null, null)

            val result = cursor!!.count > 0
            cursor.close()

            return result
        }

        fun addImagesToDownload(entryId: String, images: List<String>) {
            if (images.isNotEmpty()) {
                val values = images.map { image ->
                    ContentValues().apply {
                        put(TaskColumns.ENTRY_ID, entryId)
                        put(TaskColumns.IMG_URL_TO_DL, image)
                    }
                }.toTypedArray()
                MainApplication.getContext().contentResolver.bulkInsert(TaskColumns.CONTENT_URI, values)
            }
        }

        fun addEntriesToMobilize(entriesId: LongArray) {
            val values = entriesId.map { entryId ->
                ContentValues().apply {
                    put(TaskColumns.ENTRY_ID, entryId)
                }
            }.toTypedArray()
            MainApplication.getContext().contentResolver.bulkInsert(TaskColumns.CONTENT_URI, values)
        }
    }
}