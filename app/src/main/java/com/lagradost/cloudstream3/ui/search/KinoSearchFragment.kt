package com.lagradost.cloudstream3.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R

class KinoSearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KinoSearchScreen(
                    onMovieClick = { movie ->
                        val bundle = Bundle()
                        bundle.putString("argTitle", movie.displayTitle())
                        bundle.putInt("argId", movie.id)
                        bundle.putString("argType", movie.media_type ?: "movie")
                        findNavController().navigate(R.id.navigation_results_phone, bundle)
                    }
                )
            }
        }
    }
}
