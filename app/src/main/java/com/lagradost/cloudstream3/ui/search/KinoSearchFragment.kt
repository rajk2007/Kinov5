package com.lagradost.cloudstream3.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.R

class KinoSearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initialQuery = arguments?.getString("search_query") ?: ""
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KinoSearchScreen(
                    initialQuery = initialQuery,
                    onMovieClick = { movie ->
                        val navController = androidx.navigation.Navigation.findNavController(
                            requireActivity(),
                            R.id.nav_host_fragment
                        )
                        val bundle = Bundle().apply {
                            putString("search_query", movie.displayTitle())
                        }
                        navController.navigate(R.id.navigation_search_providers, bundle)
                    }
                )
            }
        }
    }
}
