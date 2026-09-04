package com.lagradost.cloudstream3.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.search.KinoSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                    onMovieClick = { item ->
                        val url = item.providerUrl
                        val apiName = item.providerApiName
                        if (url != null && apiName != null) {
                            val bundle = ResultFragment.newInstance(
                                url = url,
                                apiName = apiName,
                                name = item.displayTitle()
                            )
                            val navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                            navController.navigate(R.id.navigation_results_phone, bundle)
                        }
                    },
                    onLiveClick = { liveEvent ->
                        // Open Cricify details page directly
                        lifecycleScope.launch(Dispatchers.Main) {
                            val bundle = ResultFragment.newInstance(
                                url = liveEvent.url,
                                apiName = liveEvent.apiName,
                                name = liveEvent.name
                            )
                            val navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                            navController.navigate(R.id.navigation_results_phone, bundle)
                        }
                    },
                    onSearchClick = {
                        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.selectedItemId = R.id.navigation_search
                    }
                )
            }
        }
    }

}
