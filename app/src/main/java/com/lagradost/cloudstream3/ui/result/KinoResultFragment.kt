package com.lagradost.cloudstream3.ui.result

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KinoResultFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_result_kino, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ViewGroup>(R.id.action_buttons).getChildAt(1)?.setOnClickListener {
            loadLinksAndShowSelector()
        }
    }

    private fun loadLinksAndShowSelector() {
        val url = arguments?.getString("url") ?: return
        val apiName = arguments?.getString("apiName") ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val api = APIHolder.getApiFromNameNull(apiName)
            if (api == null) {
                showToast("Provider unavailable")
                return@launch
            }

            val response = try {
                APIRepository(api).load(url)
            } catch (e: Exception) {
                null
            }
            val loadResponse = (response as? Resource.Success<LoadResponse>)?.value
            if (loadResponse == null) {
                showToast("Failed to load")
                return@launch
            }

            val dataString = when (loadResponse) {
                is MovieLoadResponse -> loadResponse.dataUrl
                is TvSeriesLoadResponse -> loadResponse.episodes.firstOrNull()?.data
                else -> null
            }
            if (dataString.isNullOrBlank()) {
                showToast("No data")
                return@launch
            }

            val links = mutableListOf<ExtractorLink>()
            api.loadLinks(
                data = dataString,
                isCasting = false,
                subtitleCallback = { },
                callback = { link -> links.add(link) }
            )

            withContext(Dispatchers.Main) {
                if (links.isNotEmpty()) {
                    showQualitySelector(
                        links = links,
                        loadResponse = loadResponse,
                        dataString = dataString,
                        resultId = loadResponse.getId(),
                        isMovie = loadResponse is MovieLoadResponse,
                    )
                } else {
                    Toast.makeText(requireContext(), "No links found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showQualitySelector(
        links: List<ExtractorLink>,
        loadResponse: LoadResponse,
        dataString: String,
        resultId: Int,
        isMovie: Boolean,
    ) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(ComposeView(requireContext()).apply {
            setContent {
                DownloadQualitySheet(
                    links = links,
                    onDismiss = { dialog.dismiss() },
                    onLinkSelected = { link ->
                        startDownload(
                            link = link,
                            loadResponse = loadResponse,
                            dataString = dataString,
                            resultId = resultId,
                            apiName = loadResponse.apiName,
                            isMovie = isMovie,
                        )
                        dialog.dismiss()
                    }
                )
            }
        })
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }
}
