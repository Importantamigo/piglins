package com.github.importantamigo

import android.content.Context
import android.view.View
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.aliucord.patcher.instead
import com.aliucord.utils.ReflectUtils
import com.discord.databinding.WidgetSearchBinding
import com.discord.stores.StoreSearch
import com.discord.stores.StoreSearchData
import com.discord.stores.StoreSearchInput
import com.discord.stores.StoreSearchQuery
import com.discord.stores.StoreStream
import com.discord.utilities.view.extensions.ViewExtensions
import com.discord.widgets.search.WidgetSearch
import rx.Subscription
import rx.subjects.PublishSubject
import com.discord.utilities.search.query.node.QueryNode

@AliucordPlugin
class NoSearchPersist : Plugin() {
    override fun start(context: Context) {
        val storeSearch = StoreStream.getSearch()
        val storeSearchInput = ReflectUtils.getField(storeSearch, "storeSearchInput") as StoreSearchInput
        val storeSearchQuery = ReflectUtils.getField(storeSearch, "storeSearchQuery") as StoreSearchQuery
        val storeSearchData = ReflectUtils.getField(storeSearch, "storeSearchData") as StoreSearchData
        val forcedInputSubject = ReflectUtils.getField(storeSearchInput, "forcedInputSubject") as PublishSubject<Any>
        val updateTarget =
            StoreSearch::class.java.getDeclaredMethod("updateTarget", StoreSearch.SearchTarget::class.java)
                .apply { isAccessible = true }
        val handleSubscription =
            StoreSearch::class.java.getDeclaredMethod("handleSubscription", Subscription::class.java)
                .apply { isAccessible = true }
        val onStateChanged =
            StoreSearch::class.java.getDeclaredMethod("onStateChanged", StoreSearch.DisplayState::class.java)
                .apply { isAccessible = true }
        val getBinding = WidgetSearch::class.java.getDeclaredMethod("getBinding").apply { isAccessible = true }

        patcher.instead<StoreSearch>("clear") {
            synchronized(storeSearch) {
                updateTarget.invoke(storeSearch, null)
                handleSubscription.invoke(storeSearch, null)
                onStateChanged.invoke(storeSearch, StoreSearch.DisplayState.SUGGESTIONS)
                storeSearchQuery.clear()
                storeSearchInput.clear()
                forcedInputSubject.k.onNext(emptyList<QueryNode>())
                storeSearchData.clear()
            }
        }

        patcher.before<WidgetSearch>("onViewBound", View::class.java) {
            if (!isRecreated) {
                StoreStream.getSearch().clear()
            }
        }

        /*Scout has its own implementation to persist search field, so I need to do some shitposting*/

        patcher.after<WidgetSearch>("configureSearchInput") {
            if (!isRecreated) {
                val binding = getBinding.invoke(this) as WidgetSearchBinding
                ViewExtensions.setText(binding.c, "")
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
