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
                        bundle.putString("query", movie.displayTitle())
                        findNavController().navigate(R.id.navigation_search, bundle)
                    }
                )
            }
        }
    }
}
