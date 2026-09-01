package com.lagradost.cloudstream3.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

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
                    onMediaClick = { media ->
                        val bundle = Bundle().apply {
                            putString("url", media.url)
                            putString("apiName", media.apiName)
                            putString("name", media.name)
                        }
                        findNavController().navigate(com.lagradost.cloudstream3.R.id.navigation_results_phone, bundle)
                    }
                )
            }
        }
    }
}
