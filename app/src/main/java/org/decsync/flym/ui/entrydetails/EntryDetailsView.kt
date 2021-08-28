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

package org.decsync.flym.ui.entrydetails

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider.getUriForFile
import net.fred.feedex.R
import org.decsync.flym.data.entities.EntryWithFeed
import org.decsync.flym.data.utils.PrefConstants
import org.decsync.flym.utils.FILE_SCHEME
import org.decsync.flym.utils.HtmlUtils
import org.decsync.flym.utils.UTF8
import org.decsync.flym.utils.getPrefBoolean
import org.decsync.flym.utils.getPrefString
import org.jetbrains.anko.colorAttr
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.IOException


class EntryDetailsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : WebView(context, attrs, defStyleAttr) {

    private val TEXT_HTML = "text/html"
    private val HTML_IMG_REGEX = "(?i)<[/]?[ ]?img(.|\n)*?>"
    private val BACKGROUND_COLOR = colorString(R.attr.colorBackground)
    private val QUOTE_BACKGROUND_COLOR = colorString(R.attr.colorQuoteBackground)
    private val QUOTE_LEFT_COLOR = colorString(R.attr.colorQuoteLeft)
    private val TEXT_COLOR = colorString(R.attr.colorArticleText)
    private val SUBTITLE_COLOR = colorString(R.attr.colorSubtitle)
    private val SUBTITLE_BORDER_COLOR = "solid " + colorString(R.attr.colorSubtitleBorder)
    private val CSS = "<head><style type='text/css'> " +
            "body {max-width: 100%; margin: 0.3cm; font-family: sans-serif-light; color: " + TEXT_COLOR + "; background-color:" + BACKGROUND_COLOR + "; line-height: 150%} " +
            "* {max-width: 100%; word-break: break-word}" +
            "h1, h2 {font-weight: normal; line-height: 130%} " +
            "h1 {font-size: 170%; margin-bottom: 0.1em} " +
            "h2 {font-size: 140%} " +
            "a {color: #0099CC}" +
            "h1 a {color: inherit; text-decoration: none}" +
            "img {height: auto} " +
            "pre {white-space: pre-wrap; direction: ltr;} " +
            "blockquote {border-left: thick solid " + QUOTE_LEFT_COLOR + "; background-color:" + QUOTE_BACKGROUND_COLOR + "; margin: 0.5em 0 0.5em 0em; padding: 0.5em} " +
            "p {margin: 0.8em 0 0.8em 0} " +
            "p.subtitle {color: " + SUBTITLE_COLOR + "; border-top:1px " + SUBTITLE_BORDER_COLOR + "; border-bottom:1px " + SUBTITLE_BORDER_COLOR + "; padding-top:2px; padding-bottom:2px; font-weight:800 } " +
            "ul, ol {margin: 0 0 0.8em 0.6em; padding: 0 0 0 1em} " +
            "ul li, ol li {margin: 0 0 0.8em 0; padding: 0} " +
            "</style><meta name='viewport' content='width=device-width'/></head>"
    private val BODY_START = "<body dir=\"auto\">"
    private val BODY_END = "</body>"
    private val TITLE_START = "<h1><a href='"
    private val TITLE_MIDDLE = "'>"
    private val TITLE_END = "</a></h1>"
    private val SUBTITLE_START = "<p class='subtitle'>"
    private val SUBTITLE_END = "</p>"

    init {

        // For scrolling
        isHorizontalScrollBarEnabled = false
        settings.useWideViewPort = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = true

        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true

        // For color
        setBackgroundColor(Color.parseColor(BACKGROUND_COLOR))

        // Text zoom level from preferences
        val fontSize = context.getPrefString(PrefConstants.FONT_SIZE, "0")!!.toInt()
        if (fontSize != 0) {
            settings.textZoom = 100 + fontSize * 20
        }

        webViewClient = object : WebViewClient() {

            @Suppress("OverridingDeprecatedMember")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                try {
                    if (url.startsWith(FILE_SCHEME)) {
                        val file = File(url.replace(FILE_SCHEME, ""))
                        val contentUri = getUriForFile(context, "org.decsync.flym.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(contentUri, "image/jpeg")
                        context.startActivity(intent)
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.cant_open_link, Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                return true
            }
        }
    }

    fun setEntry(entryWithFeed: EntryWithFeed?, preferFullText: Boolean) {
        if (entryWithFeed == null) {
            loadDataWithBaseURL("", "", TEXT_HTML, UTF8, null)
        } else {
            doAsync {
                var contentText = if (preferFullText) entryWithFeed.entry.mobilizedContent
                        ?: entryWithFeed.entry.description.orEmpty() else entryWithFeed.entry.description.orEmpty()
                val displayImages = context.getPrefBoolean(PrefConstants.DISPLAY_IMAGES, true)
                contentText = if (displayImages) HtmlUtils.replaceImageURLs(contentText, entryWithFeed.entry.id) else contentText.replace(HTML_IMG_REGEX.toRegex(), "")

                uiThread {
                    if (displayImages) {
                        if (settings.blockNetworkImage) {
                            // setBlockNetworkImage(false) calls postSync, which takes time, so we clean up the html first and change the value afterwards
                            loadData("", TEXT_HTML, UTF8)
                            settings.blockNetworkImage = false
                        }
                    } else {
                        settings.blockNetworkImage = true
                    }

                    val subtitle = StringBuilder(entryWithFeed.entry.getReadablePublicationDate(context))
                    if (entryWithFeed.entry.author?.isNotEmpty() == true) {
                        subtitle.append(" &mdash; ").append(entryWithFeed.entry.author)
                    }

                    val html = StringBuilder(CSS)
                            .append(BODY_START)
                            .append(TITLE_START).append(entryWithFeed.entry.link).append(TITLE_MIDDLE).append(entryWithFeed.entry.title).append(TITLE_END)
                            .append(SUBTITLE_START).append(subtitle).append(SUBTITLE_END)
                            .append(contentText)
                            .append(BODY_END)
                            .toString()

                    // do not put 'null' to the base url...
                    loadDataWithBaseURL("", html, TEXT_HTML, UTF8, null)

                    // display top of article
                    ObjectAnimator.ofInt(this@EntryDetailsView, "scrollY", scrollY, 0).setDuration(500).start()
                }
            }
        }
    }

    private fun colorString(resourceInt: Int): String {
        val color = context.colorAttr(resourceInt)
        return String.format("#%06X", 0xFFFFFF and color)
    }
}