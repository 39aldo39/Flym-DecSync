package org.decsync.flym.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.ObsoleteCoroutinesApi
import net.fred.feedex.R
import org.decsync.flym.App
import org.decsync.flym.data.utils.PrefConstants.DECSYNC_ENABLED
import org.decsync.flym.data.utils.PrefConstants.DECSYNC_FILE
import org.decsync.flym.data.utils.PrefConstants.DECSYNC_USE_SAF
import org.decsync.flym.data.utils.PrefConstants.UPDATE_FORCES_SAF
import org.decsync.flym.service.FetcherService
import org.decsync.library.*
import org.jetbrains.anko.notificationManager
import java.io.File

val ownAppId = getAppId("Flym")
val defaultDecsyncDir = "${Environment.getExternalStorageDirectory()}/DecSync"
private const val TAG = "DecsyncUtils"
private const val ERROR_NOTIFICATION_ID = 1

class Extra

@ExperimentalStdlibApi
object DecsyncUtils {
    fun getDecsyncDir(context: Context): NativeFile {
        return if (context.getPrefBoolean(DECSYNC_USE_SAF, false)) {
            val uri = DecsyncPrefUtils.getDecsyncDir(context) ?: throw Exception(context.getString(R.string.settings_decsync_dir_not_configured))
            nativeFileFromDirUri(context, uri)
        } else {
            val file = File(context.getPrefString(DECSYNC_FILE, defaultDecsyncDir))
            nativeFileFromFile(file)
        }
    }

    @ObsoleteCoroutinesApi
    private val decsyncChannel = object: DecsyncChannel<Extra, Context>() {
        override fun isDecsyncEnabled(context: Context): Boolean {
            if (!context.getPrefBoolean(DECSYNC_ENABLED, false)) return false
            if (Build.VERSION.SDK_INT >= 29 &&
                    !Environment.isExternalStorageLegacy() &&
                    !context.getPrefBoolean(DECSYNC_USE_SAF, false)) {
                context.putPrefBoolean(DECSYNC_ENABLED, false)
                context.putPrefBoolean(DECSYNC_USE_SAF, true)
                context.putPrefBoolean(UPDATE_FORCES_SAF, true)
                return false
            }
            return true
        }

        override fun getNewDecsync(context: Context): Decsync<Extra> {
            val decsyncDir = getDecsyncDir(context)
            val decsync = Decsync<Extra>(decsyncDir, "rss", null, ownAppId)
            decsync.addMultiListener(listOf("articles", "read"), DecsyncListeners::readListener)
            decsync.addMultiListener(listOf("articles", "marked"), DecsyncListeners::markedListener)
            decsync.addListener(listOf("feeds", "subscriptions"), DecsyncListeners::subscriptionsListener)
            decsync.addListener(listOf("feeds", "names"), DecsyncListeners::feedNamesListener)
            decsync.addListener(listOf("feeds", "categories"), DecsyncListeners::categoriesListener)
            decsync.addListener(listOf("categories", "names"), DecsyncListeners::categoryNamesListener)
            decsync.addListener(listOf("categories", "parents"), DecsyncListeners::categoryParentsListener)
            return decsync
        }

        override fun onException(context: Context, e: Exception) {
            Log.e(TAG, "", e)
            context.putPrefBoolean(DECSYNC_ENABLED, false)

            val channelId = "channel_error"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                        channelId,
                        context.getString(R.string.channel_error_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                )
                context.notificationManager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_statusbar_rss)
                    .setLargeIcon(
                            BitmapFactory.decodeResource(
                                    context.resources,
                                    R.mipmap.ic_launcher
                            )
                    )
                    .setContentTitle(context.getString(R.string.decsync_disabled))
                    .setContentText(e.localizedMessage)
                    .build()
            context.notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
        }
    }

    @ObsoleteCoroutinesApi
    fun withDecsync(context: Context, action: Decsync<Extra>.() -> Unit) {
        decsyncChannel.withDecsync(context, action)
    }

    @ObsoleteCoroutinesApi
    fun initSync(context: Context) {
        decsyncChannel.initSyncWith(context) {
            // Initialize DecSync and subscribe to its feeds
            initStoredEntries()
            executeStoredEntriesForPathExact(listOf("feeds", "subscriptions"), Extra())

            // Behaves like we just inserted everything in the database
            App.initSync()

            context.startService(Intent(context, FetcherService::class.java)
                    .setAction(FetcherService.ACTION_REFRESH_FEEDS))
        }
    }
}