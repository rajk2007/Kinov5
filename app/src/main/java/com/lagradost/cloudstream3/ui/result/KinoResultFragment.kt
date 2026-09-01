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
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
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
        val name = arguments?.getString("name") ?: "Download"
        val posterUrl = arguments?.getString("posterUrl")

        lifecycleScope.launch(Dispatchers.IO) {
            val api = APIHolder.getApiFromNameNull(apiName) ?: return@launch
            val links = mutableListOf<ExtractorLink>()
            api.loadLinks(
                data = url,
                isCasting = false,
                subtitleCallback = { },
                callback = { link -> links.add(link) }
            )
            withContext(Dispatchers.Main) {
                if (links.isNotEmpty()) {
                    showQualitySelector(links, name, apiName, url, posterUrl)
                } else {
                    Toast.makeText(requireContext(), "No links found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showQualitySelector(
        links: List<ExtractorLink>,
        name: String,
        apiName: String,
        url: String,
        posterUrl: String?,
    ) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(ComposeView(requireContext()).apply {
            setContent {
                DownloadQualitySheet(
                    links = links,
                    onDismiss = { dialog.dismiss() },
                    onLinkSelected = { link ->
                        startDownload(
                            context = requireContext(),
                            link = link,
                            name = name,
                            apiName = apiName,
                            url = url,
                            type = TvType.Movie,
                            posterUrl = posterUrl,
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
