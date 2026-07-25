package com.lagradost.cloudstream3.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R

class KinoLibraryFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KinoLibraryScreen(
                    onItemClick = { title ->
                        val navController = findNavController()
                        when (title) {
                            "Downloads" -> navController.navigate(R.id.navigation_downloads)
                            "Continue Watching" -> navController.navigate(R.id.navigation_downloads) // Map to downloads or a custom history fragment
                            "History" -> {
                                // navigation_history does not exist in mobile_navigation.xml, 
                                // using navigation_downloads as fallback or if there's a better alternative.
                                // The user suggested to check exact ID.
                                navController.navigate(R.id.navigation_downloads)
                            }
                            "Watchlist" -> navController.navigate(R.id.navigation_downloads)
                            "Liked" -> navController.navigate(R.id.navigation_downloads)
                        }
                    }
                )
            }
        }
    }
}
