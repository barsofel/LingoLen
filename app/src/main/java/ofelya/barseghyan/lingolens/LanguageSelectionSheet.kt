package ofelya.barseghyan.lingolens

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class LanguageSelectionSheet(
    private val bookName: String,
    private val subtitle: String? = null,
    private val onLanguageSelected: (code: String, name: String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_language_selection, container, false)
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        super.onCreateDialog(savedInstanceState).also {
            isCancelable = false
        }

    override fun onStart() {
        super.onStart()
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            BottomSheetBehavior.from(it).apply {
                peekHeight = (88 * resources.displayMetrics.density).toInt()
                skipCollapsed = false
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvSheetSubtitle).text =
            subtitle ?: "Pick the translation language for\n\"$bookName\""

        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        view.findViewById<ImageButton>(R.id.btnCloseSheet).setOnClickListener {
            val behavior = sheet?.let { BottomSheetBehavior.from(it) } ?: run { dismiss(); return@setOnClickListener }
            if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                dismiss()
            } else {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        val allLanguages = AppLanguages.all
        val rvLanguages = view.findViewById<RecyclerView>(R.id.rvLanguages)
        val etSearch = view.findViewById<EditText>(R.id.etLanguageSearch)

        val adapter = LanguageSheetAdapter { language ->
            onLanguageSelected(language.code, language.name)
            dismiss()
        }

        rvLanguages.layoutManager = LinearLayoutManager(requireContext())
        rvLanguages.adapter = adapter
        adapter.submitList(allLanguages)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                adapter.submitList(
                    if (q.isEmpty()) allLanguages
                    else allLanguages.filter { it.name.contains(q, ignoreCase = true) }
                )
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, a: Int) {}
        })
    }
    private inner class LanguageSheetAdapter(
        private val onPick: (AppLanguages.Language) -> Unit
    ) : ListAdapter<AppLanguages.Language, LanguageSheetAdapter.VH>(DIFF) {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvFlag: TextView = itemView.findViewById(R.id.tvLangFlag)
            val tvName: TextView = itemView.findViewById(R.id.tvLangName)
            val tvCode: TextView = itemView.findViewById(R.id.tvLangCode)

            fun bind(lang: AppLanguages.Language) {
                tvFlag.text = lang.flag
                tvName.text = lang.name
                tvCode.text = lang.code.uppercase()
                itemView.setOnClickListener { onPick(lang) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language_sheet, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppLanguages.Language>() {
            override fun areItemsTheSame(a: AppLanguages.Language, b: AppLanguages.Language) = a.code == b.code
            override fun areContentsTheSame(a: AppLanguages.Language, b: AppLanguages.Language) = a == b
        }
    }
}