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
        val priorityOrder = listOf("moviebox", "castle", "cine", "dooflix", "netmirror", "pikashow", "multimovies")
        
        for (priorityName in priorityOrder) {
            val api = APIHolder.apis.find { 
                it.name.lowercase().contains(priorityName) || it::class.java.simpleName.lowercase().contains(priorityName) 
            } ?: continue
            try {
                val repo = APIRepository(api)
                val resource = withTimeoutOrNull(6000L) { repo.search(query, page = 1) }
                if (resource is Resource.Success) {
                    val firstResult = resource.value.items.firstOrNull()
                    if (firstResult != null) return firstResult
                }
            } catch (e: Exception) { 
                e.printStackTrace() 
            }
        }
        return null
    }
}
