package com.github.importantamigo

import android.content.Context
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.InsteadHook
import com.aliucord.utils.GsonUtils
import com.aliucord.utils.GsonUtils.fromJson
import com.discord.models.gifpicker.domain.ModelGifCategory
import com.discord.models.gifpicker.dto.ModelGif
import com.discord.stores.StoreGifPicker
import com.discord.stores.StoreStream
import java.io.IOException
import java.lang.reflect.Method
import java.net.URLEncoder
import java.util.Collections

@AliucordPlugin
class TenorAPIFix : Plugin() {

    init {
        settingsTab = SettingsTab(PluginSettings::class.java, SettingsTab.Type.BOTTOM_SHEET).withArgs(settings)
    }

    companion object {
        const val DEFAULT_TENOR_KEY = "3Z0688EVWYKH"
        private const val GIF_LIMIT = 50
        private const val LOCALE = "en-US"
    }

    private data class TenorMedia(val url: String?, val preview: String?, val dims: List<Int>?)
    private data class TenorResult(val id: String, val itemurl: String?, val media: List<Map<String, TenorMedia>>?)
    private data class TenorGifResponse(val results: List<TenorResult>)
    private data class TenorCategory(val searchterm: String?, val path: String?, val image: String?, val name: String?)
    private data class TenorCategoriesResponse(val tags: List<TenorCategory>?)

    private val updateTrendingGifsAccess: Method by lazy {
        StoreGifPicker::class.java.getDeclaredMethod("updateTrendingCategoryGifs", List::class.java).apply { isAccessible = true }
    }
    private val updateGifCategoriesMethod: Method by lazy {
        StoreGifPicker::class.java.getDeclaredMethod("updateGifCategories", List::class.java).apply { isAccessible = true }
    }
    private val handleSearchResultsAccess: Method by lazy {
        StoreGifPicker::class.java.getDeclaredMethod("handleGifSearchResults", String::class.java, List::class.java).apply { isAccessible = true }
    }
    private val handleTrendingErrorAccess: Method by lazy {
        StoreGifPicker::class.java.getDeclaredMethod("handleFetchTrendingGifsError").apply { isAccessible = true }
    }

    override fun start(context: Context) {
        val storeClass = StoreGifPicker::class.java

        patcher.patch(storeClass.getDeclaredMethod("fetchTrendingCategoryGifs"), InsteadHook { callFrame ->
            fetchGifsAsync(callFrame.thisObject as StoreGifPicker, null)
            null
        })

        patcher.patch(storeClass.getDeclaredMethod("fetchGifsForSearchQuery", String::class.java), InsteadHook { callFrame ->
            fetchGifsAsync(callFrame.thisObject as StoreGifPicker, callFrame.args[0] as String)
            null
        })

        patcher.patch(storeClass.getDeclaredMethod("fetchGifCategories"), InsteadHook { callFrame ->
            fetchCategoriesAsync(callFrame.thisObject as StoreGifPicker)
            null
        })
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun runOnStoreThread(block: () -> Unit) {
        StoreStream.`access$getDispatcher$p`(StoreStream.getNotices().stream).schedule {
            block()
        }
    }

    private fun fetchGifsAsync(store: StoreGifPicker, query: String?) {
        Utils.threadPool.execute {
            try {
                val gifs = fetchGifs(query)
                runOnStoreThread {
                    handleFetchSuccess(store, query, gifs)
                }
            } catch (t: Throwable) {
                logger.error("Failed to fetch GIFs from Tenor", t)
                runOnStoreThread {
                    handleFetchError(store, query)
                }
            }
        }
    }

    private fun fetchCategoriesAsync(store: StoreGifPicker) {
        Utils.threadPool.execute {
            try {
                val categories = fetchCategories()
                runOnStoreThread {
                    try {
                        updateGifCategoriesMethod.invoke(store, categories)
                    } catch (t: Throwable) {
                        logger.error("Failed to update GIF categories", t)
                    }
                }
            } catch (t: Throwable) {
                logger.error("Failed to fetch categories from Tenor", t)
            }
        }
    }

    private fun fetchTenorApi(endpoint: String, extraParams: String = ""): String {
        val key = settings.getString("apiKey", DEFAULT_TENOR_KEY)
        val url = "https://api.tenor.com/v1/$endpoint?key=$key&locale=$LOCALE$extraParams"

        val response = Http.Request(url, "GET").execute()
        val body = response.text()
        logger.debug("Tenor $endpoint response: $body")

        if (response.statusCode !in 200..299) {
            throw IOException("Tenor $endpoint request failed: ${response.statusCode} $body")
        }

        return body
    }

    private fun fetchGifs(query: String?): List<ModelGif> {
        val endpoint = if (query == null) "trending" else "search"
        val params = if (query == null) "&limit=$GIF_LIMIT" else "&q=${encode(query)}&limit=$GIF_LIMIT"

        return parseGifResponse(fetchTenorApi(endpoint, params))
    }

    private fun parseGifResponse(body: String): List<ModelGif> {
        val root = GsonUtils.gson.fromJson(body, TenorGifResponse::class.java)

        return root.results.mapNotNull { item ->
            val media = item.media?.firstOrNull() ?: return@mapNotNull null
            val tinygif = media["tinygif"]
            val gif = media["gif"]

            val gifImageUrl = tinygif?.url ?: gif?.url ?: return@mapNotNull null
            val tenorGifUrl = item.itemurl ?: gif?.url ?: gifImageUrl
            val width = gif?.dims?.getOrNull(0) ?: tinygif?.dims?.getOrNull(0) ?: 0
            val height = gif?.dims?.getOrNull(1) ?: tinygif?.dims?.getOrNull(1) ?: 0

            ModelGif(gifImageUrl, tenorGifUrl, width, height)
        }
    }

    private fun fetchCategories(): List<ModelGifCategory> {
        val body = fetchTenorApi("categories")
        val parsed = GsonUtils.gson.fromJson(body, TenorCategoriesResponse::class.java)

        return parsed.tags.orEmpty().mapNotNull { tag ->
            val image = tag.image ?: return@mapNotNull null
            val term = tag.searchterm ?: tag.name ?: return@mapNotNull null
            val displayName = tag.name ?: term

            ModelGifCategory(displayName, image)
        }
    }

    private fun handleFetchSuccess(store: StoreGifPicker, query: String?, gifs: List<ModelGif>) {
        try {
            if (query == null) {
                updateTrendingGifsAccess.invoke(store, gifs)
            } else {
                handleSearchResultsAccess.invoke(store, query, gifs)
            }
        } catch (t: Throwable) {
            logger.error("Failed to update GIF picker state", t)
        }
    }

    private fun handleFetchError(store: StoreGifPicker, query: String?) {
        try {
            if (query == null) {
                handleTrendingErrorAccess.invoke(store)
            } else {
                handleSearchResultsAccess.invoke(store, query, Collections.emptyList<ModelGif>())
            }
        } catch (t: Throwable) {
            logger.error("Failed to update GIF picker error state", t)
        }
    }

    private fun encode(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8")
        } catch (t: Throwable) {
            logger.error(t)
            value

        }
    }
}
