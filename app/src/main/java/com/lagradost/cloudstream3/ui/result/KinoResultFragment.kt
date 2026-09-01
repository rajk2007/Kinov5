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
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
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
        view.findViewById<View>(R.id.download_button)?.setOnClickListener {
            loadLinksAndShowSelector()
        }
    }

    private fun loadLinksAndShowSelector() {
        val pageUrl = arguments?.getString("url") ?: run {
            Toast.makeText(requireContext(), "Missing url", Toast.LENGTH_SHORT).show()
            return
        }
        val apiName = arguments?.getString("apiName") ?: return
        val fallbackName = arguments?.getString("name") ?: "Download"
        val posterArg = arguments?.getString("posterUrl")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = APIHolder.getApiFromNameNull(apiName)
                if (api == null) {
                    showToast("Provider not found: $apiName")
                    return@launch
                }

                val response = APIRepository(api).load(pageUrl)
                if (response !is Resource.Success) {
                    showToast("Failed to load page")
                    return@launch
                }
                val loadResponse = response.value

                val dataString = when (loadResponse) {
                    is MovieLoadResponse -> loadResponse.dataUrl
                    is LiveStreamLoadResponse -> loadResponse.dataUrl
                    is TvSeriesLoadResponse -> loadResponse.episodes.firstOrNull()?.data
                    is AnimeLoadResponse -> loadResponse.episodes.values.flatten().firstOrNull()?.data
                    else -> null
                }
                if (dataString.isNullOrBlank()) {
                    showToast("No stream data on this title")
                    return@launch
                }

                val links = mutableListOf<ExtractorLink>()
                api.loadLinks(
                    data = dataString,
                    isCasting = false,
                    subtitleCallback = { },
                    callback = { links.add(it) }
                )

                val resultId = loadResponse.getId()
                val name = loadResponse.name.ifBlank { fallbackName }
                val poster = loadResponse.posterUrl ?: posterArg
                val type = loadResponse.type

                withContext(Dispatchers.Main) {
                    if (links.isEmpty()) {
                        Toast.makeText(requireContext(), "No links found", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val dialog = Dialog(requireContext())
                    dialog.setContentView(ComposeView(requireContext()).apply {
                        setContent {
                            DownloadQualitySheet(
                                links = links.distinctBy { it.url },
                                onLinkSelected = { link ->
                                    startDownload(
                                        link = link,
                                        name = name,
                                        apiName = apiName,
                                        pageUrl = pageUrl,
                                        dataString = dataString,
                                        resultId = resultId,
                                        type = type,
                                        posterUrl = poster,
                                    )
                                    dialog.dismiss()
                                },
                                onDismiss = { dialog.dismiss() },
                            )
                        }
                    })
                    dialog.setOnShowListener {
                        dialog.window?.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                    dialog.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error: ${e.message ?: "Unable to load download links"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
