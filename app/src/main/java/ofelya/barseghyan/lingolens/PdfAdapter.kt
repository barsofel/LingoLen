package ofelya.barseghyan.lingolens

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PdfAdapter(
    private val pdfFiles: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    class PdfViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPdfName: TextView = view.findViewById(R.id.tvPdfName)
        val tvPdfSize: TextView = view.findViewById(R.id.tvPdfSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf, parent, false)
        return PdfViewHolder(view)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val file = pdfFiles[position]

        holder.tvPdfName.text = file.name
        holder.tvPdfSize.text = "${file.length() / 1024} KB"

        holder.itemView.setOnClickListener {
            onClick(file)
        }
    }

    override fun getItemCount(): Int = pdfFiles.size
}