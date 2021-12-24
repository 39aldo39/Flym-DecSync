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

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import com.google.android.material.appbar.AppBarLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_entry_details.*
import kotlinx.coroutines.ObsoleteCoroutinesApi
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeGestureListener
import net.fred.feedex.R
import org.decsync.flym.App
import org.decsync.flym.data.entities.EntryWithFeed
import org.decsync.flym.data.utils.PrefConstants
import org.decsync.flym.data.utils.PrefConstants.ENABLE_SWIPE_ENTRY
import org.decsync.flym.data.utils.PrefConstants.HIDE_NAVIGATION_ON_SCROLL
import org.decsync.flym.service.FetcherService
import org.decsync.flym.ui.main.MainNavigator
import org.decsync.flym.utils.getPrefBoolean
import org.decsync.flym.utils.isGestureNavigationEnabled
import org.decsync.flym.utils.isOnline
import org.jetbrains.anko.attr
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.support.v4.browse
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.share
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.uiThread
import org.jetbrains.annotations.NotNull


@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
class EntryDetailsFragment : Fragment() {

    companion object {

        const val ARG_ENTRY_ID = "ARG_ENTRY_ID"
        const val ARG_ALL_ENTRIES_IDS = "ARG_ALL_ENTRIES_IDS"

        fun newInstance(entryId: String, allEntryIds: List<String>): EntryDetailsFragment {
            return EntryDetailsFragment().apply {
                arguments = bundleOf(ARG_ENTRY_ID to entryId, ARG_ALL_ENTRIES_IDS to allEntryIds)
            }
        }
    }

    private val navigator: MainNavigator? by lazy { activity as? MainNavigator }

    private lateinit var entryId: String
    private var entryWithFeed: EntryWithFeed? = null
    private var allEntryIds = emptyList<String>()
        set(value) {
            field = value

            val currentIdx = value.indexOf(entryId)

            previousId = if (currentIdx <= 0) {
                null
            } else {
                value[currentIdx - 1]
            }

            nextId = if (currentIdx < 0 || currentIdx >= value.size - 1) {
                null
            } else {
                value[currentIdx + 1]
            }
        }
    private var previousId: String? = null
    private var nextId: String? = null
    private var isMobilizingLiveData: LiveData<Int>? = null
    private var isMobilizing = false
    private var preferFullText = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            inflater.inflate(R.layout.fragment_entry_details, container, false)
        } else {
            inflater.inflate(R.layout.fragment_entry_details_noswipe, container, false)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        entry_view.destroy()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        refresh_layout.setColorScheme(R.color.colorAccent,
            requireContext().attr(R.attr.colorPrimaryDark).resourceId,
            R.color.colorAccent,
            requireContext().attr(R.attr.colorPrimaryDark).resourceId)

        refresh_layout.setOnRefreshListener {
            switchFullTextMode()
        }

        if (defaultSharedPreferences.getString(PrefConstants.THEME, null) == "LIGHT") {
            navigate_before?.imageResource = R.drawable.ic_navigate_before_black_24dp
            navigate_next?.imageResource = R.drawable.ic_navigate_next_black_24dp
        }

        if (defaultSharedPreferences.getBoolean(ENABLE_SWIPE_ENTRY, true)) {
            swipe_view.swipeGestureListener = object : SwipeGestureListener {
                override fun onSwipedLeft(@NotNull swipeActionView: SwipeActionView): Boolean {
                    nextId?.let { nextId ->
                        setEntry(nextId, allEntryIds)
                        navigator?.setSelectedEntryId(nextId)
                        app_bar_layout.setExpanded(true, true)
                        nested_scroll_view.scrollTo(0, 0)
                    }
                    return true
                }

                override fun onSwipedRight(@NotNull swipeActionView: SwipeActionView): Boolean {
                    previousId?.let { previousId ->
                        setEntry(previousId, allEntryIds)
                        navigator?.setSelectedEntryId(previousId)
                        app_bar_layout.setExpanded(true, true)
                        nested_scroll_view.scrollTo(0, 0)
                    }
                    return true
                }
            }
        }

        if (defaultSharedPreferences.getBoolean(HIDE_NAVIGATION_ON_SCROLL, false)) {
            toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
                scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
            }
            activity?.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
            ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                if (swipe_view != null) {
                    swipe_view.updateLayoutParams<FrameLayout.LayoutParams> {
                        leftMargin = systemInsets.left
                        rightMargin = systemInsets.right
                    }
                } else {
                    coordinator.updateLayoutParams<FrameLayout.LayoutParams> {
                        leftMargin = systemInsets.left
                        rightMargin = systemInsets.right
                    }
                }
                toolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
                    topMargin = systemInsets.top
                }
                entry_view.updateLayoutParams<FrameLayout.LayoutParams> {
                    bottomMargin = systemInsets.bottom
                }
                insets
            }
            val statusBarBackground = ResourcesCompat.getColor(resources, R.color.status_bar_background, null)
            activity?.window?.statusBarColor = statusBarBackground
            activity?.window?.navigationBarColor = if (context?.isGestureNavigationEnabled() == true) Color.TRANSPARENT else statusBarBackground
        }

        setEntry(arguments?.getString(ARG_ENTRY_ID)!!, arguments?.getStringArrayList(ARG_ALL_ENTRIES_IDS)!!)
    }

    private fun initDataObservers() {
        isMobilizingLiveData?.removeObservers(viewLifecycleOwner)
        refresh_layout.isRefreshing = false

        isMobilizingLiveData = App.db.taskDao().observeItemMobilizationTasksCount(entryId)
        isMobilizingLiveData?.observe(viewLifecycleOwner, { count ->
            if (count ?: 0 > 0) {
                isMobilizing = true
                refresh_layout.isRefreshing = true

                // If the service is not started, start it here to avoid an infinite loading
                if (context?.getPrefBoolean(PrefConstants.IS_REFRESHING, false) == false) {
                    context?.startService(Intent(context, FetcherService::class.java).setAction(FetcherService.ACTION_MOBILIZE_FEEDS))
                }
            } else {
                if (isMobilizing) {
                    doAsync {
                        App.db.entryDao().findByIdWithFeed(entryId)?.let { newEntry ->
                            uiThread {
                                entryWithFeed = newEntry
                                entry_view.setEntry(entryWithFeed, true)

                                setupToolbar()
                            }
                        }
                    }
                }

                isMobilizing = false
                refresh_layout.isRefreshing = false
            }
        })
    }

    private fun setupToolbar() {
        toolbar.apply {
            entryWithFeed?.let { entryWithFeed ->
                title = entryWithFeed.feedTitle

                menu.clear()
                inflateMenu(R.menu.menu_fragment_entry_details)

                if (activity?.containers_layout?.hasTwoColumns() != true) {
                    setNavigationIcon(R.drawable.ic_back_white_24dp)
                    setNavigationOnClickListener { activity?.onBackPressed() }
                }

                if (entryWithFeed.entry.favorite) {
                    menu.findItem(R.id.menu_entry_details__favorite)
                        .setTitle(R.string.menu_unstar)
                        .setIcon(R.drawable.ic_star_white_24dp)
                }


                if (entryWithFeed.entry.mobilizedContent == null || !preferFullText) {
                    menu.findItem(R.id.menu_entry_details__fulltext).isVisible = true
                    menu.findItem(R.id.menu_entry_details__original_text).isVisible = false
                } else {
                    menu.findItem(R.id.menu_entry_details__fulltext).isVisible = false
                    menu.findItem(R.id.menu_entry_details__original_text).isVisible = true
                }

                setOnMenuItemClickListener { item ->
                    when (item?.itemId) {
                        R.id.menu_entry_details__favorite -> {
                            entryWithFeed.entry.favorite = !entryWithFeed.entry.favorite
                            entryWithFeed.entry.read = true // otherwise it marked it as unread again

                            if (entryWithFeed.entry.favorite) {
                                item.setTitle(R.string.menu_unstar).setIcon(R.drawable.ic_star_white_24dp)
                            } else {
                                item.setTitle(R.string.menu_star).setIcon(R.drawable.ic_star_border_white_24dp)
                            }

                            doAsync {
                                App.db.entryDao().update(entryWithFeed.entry)
                            }
                        }
                        R.id.menu_entry_details__open_browser -> {
                            entryWithFeed.entry.link?.let {
                                browse(it)
                            }
                        }
                        R.id.menu_entry_details__share -> {
                            share(entryWithFeed.entry.link.orEmpty(), entryWithFeed.entry.title.orEmpty())
                        }
                        R.id.menu_entry_details__fulltext -> {
                            switchFullTextMode()
                        }
                        R.id.menu_entry_details__original_text -> {
                            switchFullTextMode()
                        }
                        R.id.menu_entry_details__mark_as_unread -> {
                            doAsync {
                                App.db.entryDao().markAsUnread(listOf(entryId))
                            }
                            if (activity?.containers_layout?.hasTwoColumns() != true) {
                                activity?.onBackPressed()
                            }
                        }
                    }

                    true
                }
            }
        }
    }

    private fun switchFullTextMode() {
        // Enable this to test new manual mobilization
//		doAsync {
//			entryWithFeed?.entry?.let {
//				it.mobilizedContent = null
//				App.db.entryDao().insert(it)
//			}
//		}

        entryWithFeed?.let { entryWithFeed ->
            if (entryWithFeed.entry.mobilizedContent == null || !preferFullText) {
                if (entryWithFeed.entry.mobilizedContent == null) {
                    this@EntryDetailsFragment.context?.let { c ->
                        if (c.isOnline()) {
                            doAsync {
                                FetcherService.addEntriesToMobilize(listOf(entryWithFeed.entry.id))
                                c.startService(Intent(c, FetcherService::class.java).setAction(FetcherService.ACTION_MOBILIZE_FEEDS))
                            }
                        } else {
                            refresh_layout.isRefreshing = false
                            toast(R.string.network_error).show()
                        }
                    }
                } else {
                    refresh_layout.isRefreshing = false
                    preferFullText = true
                    entry_view.setEntry(entryWithFeed, preferFullText)

                    setupToolbar()
                }
            } else {
                refresh_layout.isRefreshing = isMobilizing
                preferFullText = false
                entry_view.setEntry(entryWithFeed, preferFullText)

                setupToolbar()
            }
        }
    }

    fun setEntry(entryId: String, allEntryIds: List<String>) {
        this.entryId = entryId
        this.allEntryIds = allEntryIds
        arguments?.putString(ARG_ENTRY_ID, entryId)
        arguments?.putStringArrayList(ARG_ALL_ENTRIES_IDS, ArrayList(allEntryIds))

        doAsync {
            App.db.entryDao().findByIdWithFeed(entryId)?.let { entry ->
                val feed = App.db.feedDao().findById(entry.entry.feedId)
                entryWithFeed = entry
                preferFullText = feed?.retrieveFullText ?: true
                isMobilizing = false

                uiThread {
                    entry_view.setEntry(entryWithFeed, preferFullText)

                    initDataObservers()

                    setupToolbar()
                }
            }

            App.db.entryDao().markAsRead(listOf(entryId))
        }
    }
}
