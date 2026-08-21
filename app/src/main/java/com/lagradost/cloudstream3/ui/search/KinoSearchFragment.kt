package com.lagradost.cloudstream3.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.lagradost.cloudstream3.R

class KinoSearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initialQuery = arguments?.getString("search_query")
            ?: com.lagradost.cloudstream3.MainActivity.nextSearchQuery
            ?: ""

        // Clear the global query so it doesn't trigger again
        if (com.lagradost.cloudstream3.MainActivity.nextSearchQuery != null) {
            com.lagradost.cloudstream3.MainActivity.nextSearchQuery = null
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KinoSearchScreen(
                    initialQuery = initialQuery,
                    onResultClick = { result ->
                        val bundle = Bundle().apply {
                            putString("url", result.url)
                            putString("apiName", result.apiName)
                            putString("name", result.name)
                        }
                        val navController = Navigation.findNavController(
                            requireActivity(),
                            R.id.nav_host_fragment
                        )
                        navController.navigate(R.id.navigation_results_phone, bundle)
                    }
                )
            }
        }
    }
}
