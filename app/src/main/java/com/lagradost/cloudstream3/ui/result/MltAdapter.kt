package com.lagradost.cloudstream3.ui.result

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class MltAdapter(
    private val clickCallback: (SearchClickCallback) -> Unit,
) : RecyclerView.Adapter<MltAdapter.Holder>() {
    private var items: List<SearchResponse> = emptyList()

    fun submitList(value: List<SearchResponse>) {
        items = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = parent.resources.displayMetrics.density
        val width = (97 * density).toInt()
        val imageHeight = (145 * density).toInt()
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (10 * density).toInt()
            }
            setPadding(0, 0, 0, (8 * density).toInt())
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        val image = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(width, imageHeight)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = context.getString(R.string.search_poster_img_des)
            setBackgroundColor(Color.rgb(14, 14, 16))
        }
        val title = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (5 * density).toInt()
            }
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val year = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextColor(Color.GRAY)
            textSize = 11f
        }
        root.addView(image)
        root.addView(title)
        root.addView(year)
        return Holder(root, image, title, year)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.image.loadImage(item.posterUrl)
        holder.title.text = item.name
        holder.year.text = when (item) {
            is AnimeSearchResponse -> item.year
            is MovieSearchResponse -> item.year
            is TvSeriesSearchResponse -> item.year
            else -> null
        }?.toString().orEmpty()
        holder.itemView.setOnClickListener {
            clickCallback(SearchClickCallback(SEARCH_ACTION_LOAD, holder.itemView, position, item))
        }
    }

    override fun getItemCount(): Int = items.size

    class Holder(
        view: View,
        val image: ImageView,
        val title: TextView,
        val year: TextView,
    ) : RecyclerView.ViewHolder(view)
}
