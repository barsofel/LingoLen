package ofelya.barseghyan.lingolens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object PdfDownloader {

    suspend fun downloadPdf(context: Context, pdfUrl: String, fileName: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(pdfUrl)
                val connection = url.openConnection()
                connection.connect()

                val file = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                FileOutputStream(file).use { output ->
                    url.openStream().use { input ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF downloaded: $fileName", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}