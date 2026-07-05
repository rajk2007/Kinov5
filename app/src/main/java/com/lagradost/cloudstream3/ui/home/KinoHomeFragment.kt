package com.lagradost.cloudstream3.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class KinoHomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KinoHomeScreen(
                    onMovieClick = { movie ->
                        // Show loading toast
                        android.widget.Toast.makeText(context, "Searching for sources...", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // Search providers for this movie title, then open ResultFragment
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = findFirstProviderResult(movie.displayTitle())
                            
                            withContext(Dispatchers.Main) {
                                if (result != null) {
                                    val bundle = ResultFragment.newInstance(
                                        url = result.url,
                                        apiName = result.apiName,
                                        name = result.name
                                    )
                                    val navController = Navigation.findNavController(
                                        requireActivity(),
                                        R.id.nav_host_fragment
                                    )
                                    navController.navigate(R.id.navigation_results_phone, bundle)
                                } else {
                                    // Fallback: open search screen with query
                                    com.lagradost.cloudstream3.MainActivity.nextSearchQuery = movie.displayTitle()
                                    activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
                                        ?.selectedItemId = R.id.navigation_search
                                }
                            }
                        }
                    },
                    onSearchClick = {
                        activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
                            ?.selectedItemId = R.id.navigation_search
                    }
                )
            }
        }
    }

    private suspend fun findFirstProviderResult(query: String): SearchResponse? {
        // 1. Try MovieBox strictly first for maximum speed
        val movieBoxApi = APIHolder.apis.find { it.name.lowercase().contains("moviebox") }
        if (movieBoxApi != null) {
            try {
                val repo = APIRepository(movieBoxApi)
                val resource = withTimeoutOrNull(4000L) { repo.search(query, page = 1) }
                if (resource is Resource.Success) {
                    resource.value.items.firstOrNull()?.let { return it }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 2. Fallback to CastleTV if MovieBox failed
        val castleApi = APIHolder.apis.find { it.name.lowercase().contains("castletv") }
        if (castleApi != null) {
            try {
                val repo = APIRepository(castleApi)
                val resource = withTimeoutOrNull(4000L) { repo.search(query, page = 1) }
                if (resource is Resource.Success) {
                    resource.value.items.firstOrNull()?.let { return it }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 3. Fallback to CineTV
        val cineApi = APIHolder.apis.find { it.name.lowercase().contains("cinetv") }
        if (cineApi != null) {
            try {
                val repo = APIRepository(cineApi)
                val resource = withTimeoutOrNull(4000L) { repo.search(query, page = 1) }
                if (resource is Resource.Success) {
                    resource.value.items.firstOrNull()?.let { return it }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        return null
    }
}
