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
                        try {
                            val bundle = Bundle()
                            bundle.putString("url", movie.id.toString())
                            bundle.putString("apiName", "TmdbProvider")
                            bundle.putString("name", movie.displayTitle())
                            findNavController().navigate(R.id.navigation_results_phone, bundle)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Error loading details", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}
