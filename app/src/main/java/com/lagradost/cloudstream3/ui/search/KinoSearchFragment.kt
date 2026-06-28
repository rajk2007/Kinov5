package com.lagradost.cloudstream3.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.api.MovieResult

class KinoSearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = requireContext()
                KinoSearchScreen(
                    onMovieClick = { movie ->
                        val intent = android.content.Intent(context, com.lagradost.cloudstream3.MainActivity::class.java).apply {
                            action = android.content.Intent.ACTION_SEARCH
                            putExtra(android.app.SearchManager.QUERY, movie.displayTitle())
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}
