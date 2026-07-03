package com.lagradost.cloudstream3.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R

class KinoHomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val navController = findNavController()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KinoHomeScreen(
                    onMovieClick = { movie ->
                        val bundle = Bundle().apply {
                            putString("url", movie.id.toString())
                            putString("apiName", "TmdbProvider")
                            putString("name", movie.displayTitle())
                            putBoolean("restart", true)
                        }
                        navController.navigate(R.id.navigation_results_phone, bundle)
                    },
                    onSearchClick = {
                        navController.navigate(R.id.navigation_search)
                    }
                )
            }
        }
    }
}
