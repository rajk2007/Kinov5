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
import com.lagradost.cloudstream3.ui.result.ResultFragment

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
                        val bundle = ResultFragment.newInstance(
                            url = media.url,
                            apiName = media.apiName,
                            name = media.name,
                            startAction = 2, // START_ACTION_LOAD_EP
                            startValue = media.episodeId ?: 0,
                        )
                        findNavController().navigate(R.id.navigation_results_phone, bundle)
                    }
                )
            }
        }
    }
}
