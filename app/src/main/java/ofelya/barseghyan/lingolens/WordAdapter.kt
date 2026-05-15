package ofelya.barseghyan.lingolens

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class WordAdapter(
    // The fragment passes its mutableListOf<WordEntry> directly so we share
    // the same reference — no copy needed.
    private val words: MutableList<WordEntry>,
    private val onDelete: (WordEntry) -> Unit
) : RecyclerView.Adapter<WordAdapter.ViewHolder>() {

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTranslation: TextView  = itemView.findViewById(R.id.tvTranslation)
        val tvWord: TextView         = itemView.findViewById(R.id.tvWord)
        val tvDate: TextView         = itemView.findViewById(R.id.tvDate)
        val btnDelete: ImageButton   = itemView.findViewById(R.id.btnDelete)
    }

    // -------------------------------------------------------------------------
    // Adapter overrides
    // -------------------------------------------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = words[position]

        // Top line → original word being translated
        holder.tvWord.text = entry.word

        // Bottom line → translated result (or a pending indicator)
        holder.tvTranslation.text =
            if (entry.translation.isBlank()) "Translating…" else entry.translation

        // Date
        holder.tvDate.text = entry.dateSaved

        // Delete button
        holder.btnDelete.setOnClickListener { onDelete(entry) }
    }

    override fun getItemCount(): Int = words.size

    // -------------------------------------------------------------------------
    // DiffUtil — only re-binds rows whose content actually changed.
    // This prevents "Translating…" from flickering back after a DB update.
    // -------------------------------------------------------------------------

    fun submitList(newList: List<WordEntry>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = words.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                words[oldPos].id == newList[newPos].id

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                words[oldPos] == newList[newPos]
        })

        words.clear()
        words.addAll(newList)
        diff.dispatchUpdatesTo(this)
    }

    /** Legacy helper kept so existing call sites still compile. */
    fun updateList(newList: List<WordEntry>) = submitList(newList)
}