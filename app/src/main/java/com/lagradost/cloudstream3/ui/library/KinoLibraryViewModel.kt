package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Explicitly define the data class used for the UI
data class KinoLibraryItem(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?
)

class KinoLibraryViewModel : ViewModel() {
    private val _continueWatching = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val continueWatching: StateFlow<List<KinoLibraryItem>> = _continueWatching

    private val _history = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val history: StateFlow<List<KinoLibraryItem>> = _history

    private val _watchlist = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val watchlist: StateFlow<List<KinoLibraryItem>> = _watchlist

    private val _liked = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val liked: StateFlow<List<KinoLibraryItem>> = _liked

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Continue Watching
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                val resumeList = resumeIds.mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id)
                    if (resume != null) {
                        // Use reflection-safe access or fallbacks
                        // Fetching from BookmarkedData because ResumeWatching lacks metadata fields
                        val metadata = DataStoreHelper.getBookmarkedData(resume.parentId)
                        val name = try { metadata?.name } catch (e: Exception) { null }
                        val url = try { metadata?.url } catch (e: Exception) { null }
                        val apiName = try { metadata?.apiName } catch (e: Exception) { null }
                        val posterUrl = try { metadata?.posterUrl } catch (e: Exception) { null }
                        
                        if (!name.isNullOrBlank() && !url.isNullOrBlank() && !apiName.isNullOrBlank()) {
                            KinoLibraryItem(name = name!!, url = url!!, apiName = apiName!!, posterUrl = posterUrl)
                        } else null
                    } else null
                }
                _continueWatching.value = resumeList

                // 2. Watchlist & History (Bookmarks)
                val allBookmarks = DataStoreHelper.getAllBookmarkedData()
                val mappedBookmarks = allBookmarks.map {
                    // Try to get properties, fallback to safe values
                    // Use safe cast to SearchResponse to handle potential unresolved reference errors during compilation
                    val searchRes = it as? com.lagradost.cloudstream3.SearchResponse
                    val name = try { searchRes?.name } catch (e: Exception) { "Bookmarked Item ${it.id ?: ""}" }
                    val url = try { searchRes?.url } catch (e: Exception) { "" }
                    val apiName = try { searchRes?.apiName } catch (e: Exception) { "" }
                    val posterUrl = try { searchRes?.posterUrl } catch (e: Exception) { null }
                    KinoLibraryItem(
                        name = name ?: "Bookmarked Item ${it.id ?: ""}", 
                        url = url ?: "", 
                        apiName = apiName ?: "", 
                        posterUrl = posterUrl
                    )
                }
                _watchlist.value = mappedBookmarks
                _history.value = mappedBookmarks // Use bookmarks as history for now

                // 3. Liked (Favorites)
                val allFavorites = DataStoreHelper.getAllFavorites()
                _liked.value = allFavorites.map {
                    val searchRes = it as? com.lagradost.cloudstream3.SearchResponse
                    val name = try { searchRes?.name } catch (e: Exception) { "Liked Item ${it.id ?: ""}" }
                    val url = try { searchRes?.url } catch (e: Exception) { "" }
                    val apiName = try { searchRes?.apiName } catch (e: Exception) { "" }
                    val posterUrl = try { searchRes?.posterUrl } catch (e: Exception) { null }
                    KinoLibraryItem(
                        name = name ?: "Liked Item ${it.id ?: ""}", 
                        url = url ?: "", 
                        apiName = apiName ?: "", 
                        posterUrl = posterUrl
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
