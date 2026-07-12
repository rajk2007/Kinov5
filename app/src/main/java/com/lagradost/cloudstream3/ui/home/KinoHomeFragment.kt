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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.search.KinoSearchResult
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
                        android.widget.Toast.makeText(context, "Searching for sources...", android.widget.Toast.LENGTH_SHORT).show()

                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = findFirstProviderResult(movie.displayTitle())
                            withContext(Dispatchers.Main) {
                                if (result != null) {
                                    val bundle = ResultFragment.newInstance(
                                        url = result.url,
                                        apiName = result.apiName,
                                        name = result.name
                                    )
                                    val navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                                    navController.navigate(R.id.navigation_results_phone, bundle)
                                } else {
                                    MainActivity.nextSearchQuery = movie.displayTitle()
                                    activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.selectedItemId = R.id.navigation_search
                                }
                            }
                        }
                    },
                    onLiveClick = { liveEvent ->
                        // Open Cricify details page directly
                        lifecycleScope.launch(Dispatchers.Main) {
                            val bundle = ResultFragment.newInstance(
                                url = liveEvent.url,
                                apiName = liveEvent.apiName,
                                name = liveEvent.name
                            )
                            val navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                            navController.navigate(R.id.navigation_results_phone, bundle)
                        }
                    },
                    onSearchClick = {
                        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.selectedItemId = R.id.navigation_search
                    }
                )
            }
        }
    }

    private suspend fun findFirstProviderResult(query: String): SearchResponse? {
        if (APIHolder.apis.isEmpty()) return null

        val targetProviders = listOf("moviebox", "castle", "netmirror", "netflix", "pikashow")
        val providers = APIHolder.apis.filter { api ->
            val nameLower = api.name.lowercase()
            val classLower = api::class.java.simpleName.lowercase()
            targetProviders.any { nameLower.contains(it) || classLower.contains(it) }
        }

        for (api in providers) {
            try {
                val repo = APIRepository(api)
                val resource = withTimeoutOrNull(4000L) { repo.search(query, page = 1) }
                if (resource is Resource.Success) {
                    return resource.value.items.firstOrNull()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }
}
