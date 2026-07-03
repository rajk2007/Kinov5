package com.lagradost.cloudstream3.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.ResultFragment

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
                    onResultClick = { result ->
                        // Open ResultFragment with correct Bundle keys
                        val bundle = ResultFragment.newInstance(
                            url = result.url,
                            apiName = result.apiName,
                            name = result.name
                        )
                        val navController = Navigation.findNavController(
                            requireActivity(),
                            R.id.nav_host_fragment
                        )
                        navController.navigate(R.id.navigation_results_phone, bundle)
                    }
                )
            }
        }
    }
}
