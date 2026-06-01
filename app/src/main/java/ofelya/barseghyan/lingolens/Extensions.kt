package ofelya.barseghyan.lingolens

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import ofelya.barseghyan.lingolens.R


fun setupGlobalHeader(fragmentView: View, customTitle: String) {
    val header = fragmentView.findViewById<View>(R.id.globalHeader) ?: return

    val tvTitle = header.findViewById<TextView>(R.id.tvHeaderTitle)
    tvTitle?.text = customTitle

    val ivProfileCircle = header.findViewById<ImageView>(R.id.ivProfileCircle)
    ivProfileCircle?.setOnClickListener {
        val context = fragmentView.context
        val intent = Intent(context, SettingsActivity::class.java)
        context.startActivity(intent)
    }
}