package ofelya.barseghyan.lingolens

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class BookAdapter(
    private val onClick:  (Book) -> Unit,
    private val onDelete: (Book) -> Unit
) : ListAdapter<Book, BookAdapter.BookViewHolder>(DiffCallback) {

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName:     TextView    = itemView.findViewById(R.id.tvBookName)
        val tvLanguage: TextView    = itemView.findViewById(R.id.tvBookLanguage)
        val btnDelete:  ImageButton = itemView.findViewById(R.id.btnDeleteBook)

        fun bind(book: Book) {
            tvName.text = book.name

            if (book.targetLanguageCode.isNotEmpty()) {
                val lang = AppLanguages.byCode(book.targetLanguageCode)
                tvLanguage.text       = "${lang?.flag ?: ""} ${book.targetLanguageName}"
                tvLanguage.visibility = View.VISIBLE
            } else {
                tvLanguage.visibility = View.GONE
            }

            itemView.setOnClickListener { onClick(book) }
            btnDelete.setOnClickListener { onDelete(book) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(a: Book, b: Book) = a.id == b.id
        override fun areContentsTheSame(a: Book, b: Book) = a == b
    }
}