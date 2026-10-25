package hu.tvbrowser

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class BookmarkAdapter(
    private var bookmarks: List<BookmarkItem>,
    private val onClick: (BookmarkItem) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.cardBookmark)
        val textTitle: TextView = view.findViewById(R.id.textBookmarkTitle)
        val textUrl: TextView = view.findViewById(R.id.textBookmarkUrl)
        val viewBadge: View = view.findViewById(R.id.viewBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bookmark_tile, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val item = bookmarks[position]
        holder.textTitle.text = item.title
        holder.textUrl.text = item.url

        try {
            val badgeDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(item.color))
            }
            holder.viewBadge.background = badgeDrawable
        } catch (e: Exception) {
            holder.viewBadge.setBackgroundColor(Color.BLUE)
        }

        // Távirányító kattintás esemény
        holder.cardView.setOnClickListener {
            onClick(item)
        }

        // Fókusz animáció (Leanback kiemelés a TV távirányítóhoz)
        holder.cardView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start()
                holder.cardView.cardElevation = 16f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                holder.cardView.cardElevation = 4f
            }
        }
    }

    override fun getItemCount(): Int = bookmarks.size

    fun updateData(newItems: List<BookmarkItem>) {
        this.bookmarks = newItems
        notifyDataSetChanged()
    }
}
