package com.lagradost.cloudstream3.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R

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
                        // Set global search query and select Search tab
                        MainActivity.nextSearchQuery = movie.displayTitle()
                        selectSearchTab()
                    },
                    onSearchClick = {
                        // Just select the Search tab - don't push a new fragment
                        selectSearchTab()
                    }
                )
            }
        }
    }

    private fun selectSearchTab() {
        activity?.findViewById<BottomNavigationView>(R.id.nav_view)
            ?.selectedItemId = R.id.navigation_search
        activity?.findViewById<NavigationRailView>(R.id.nav_rail_view)
            ?.selectedItemId = R.id.navigation_search
    }
}
