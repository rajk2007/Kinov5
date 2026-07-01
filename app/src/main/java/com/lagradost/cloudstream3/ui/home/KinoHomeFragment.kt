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
import com.lagradost.cloudstream3.api.MovieResult

class KinoHomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = requireContext()
                KinoHomeScreen(
                    onMovieClick = { movie ->
                        val bundle = Bundle()
                        bundle.putInt("argId", movie.id)
                        bundle.putString("argName", movie.displayTitle())
                        bundle.putString("argPoster", movie.poster_path ?: "")

                        // Map TMDB media_type to CloudStream type (0=Movie, 1=TV)
                        val type = when (movie.media_type) {
                            "movie" -> 0
                            "tv" -> 1
                            else -> 0 // Default to movie
                        }
                        bundle.putInt("argType", type)

                        findNavController().navigate(R.id.navigation_results_phone, bundle)
                    }
                )
            }
        }
    }
}
