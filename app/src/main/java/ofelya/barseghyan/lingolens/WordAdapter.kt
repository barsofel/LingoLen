package ofelya.barseghyan.lingolens

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WordAdapter(
    private val onDelete: (WordEntry) -> Unit
) : RecyclerView.Adapter<WordAdapter.ViewHolder>() {

    private val words = mutableListOf<WordEntry>()

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var playingPosition: Int = -1


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvWord: TextView        = itemView.findViewById(R.id.tvWord)
        val tvTranslation: TextView = itemView.findViewById(R.id.tvTranslation)
        val tvDate: TextView        = itemView.findViewById(R.id.tvDate)
        val btnDelete: ImageButton  = itemView.findViewById(R.id.btnDelete)
        val btnSpeak: ImageButton   = itemView.findViewById(R.id.btnSpeak)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word2, parent, false)

        if (tts == null) {
            initTts(parent.context.applicationContext)
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = words[position]

        holder.tvWord.text = entry.word
        holder.tvTranslation.text =
            if (entry.translation.isBlank()) "Translating…" else entry.translation
        holder.tvDate.text = entry.dateSaved

        val isPlaying = position == playingPosition
        holder.btnSpeak.alpha = if (isPlaying) 0.5f else 1.0f

        holder.btnDelete.setOnClickListener { onDelete(entry) }

        holder.btnSpeak.setOnClickListener {
            speakWord(entry.word, position, holder.itemView.context)
        }
    }

    override fun getItemCount(): Int = words.size


    fun submitList(newList: List<WordEntry>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = words.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                words[oldPos].id == newList[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                words[oldPos] == newList[newPos]
        })
        words.clear()
        words.addAll(newList)
        diff.dispatchUpdatesTo(this)
    }

    fun updateList(newList: List<WordEntry>) = submitList(newList)


    private fun speakWord(word: String, position: Int, context: Context) {
        val prevPlaying = playingPosition
        playingPosition = position
        if (prevPlaying != -1) notifyItemChanged(prevPlaying)
        notifyItemChanged(position)

        CoroutineScope(Dispatchers.IO).launch {
            val audioUrl = fetchAudioUrl(word)

            withContext(Dispatchers.Main) {
                if (audioUrl != null) {
                    playFromUrl(audioUrl, position)
                } else {
                    speakWithTts(word, position)
                }
            }
        }
    }


    private fun fetchAudioUrl(word: String): String? {
        return try {
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout    = 3000
            conn.connect()

            if (conn.responseCode != 200) return null

            val json = conn.inputStream.bufferedReader().readText()
            val array = JSONArray(json)
            val phonetics = array.getJSONObject(0).getJSONArray("phonetics")

            for (i in 0 until phonetics.length()) {
                val audio = phonetics.getJSONObject(i).optString("audio", "")
                if (audio.isNotBlank()) return audio
            }
            null
        } catch (e: Exception) {
            null
        }
    }


    private fun playFromUrl(url: String, position: Int) {
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnCompletionListener {
                    if (playingPosition == position) {
                        playingPosition = -1
                        notifyItemChanged(position)
                    }
                    release()
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    false
                }
                prepareAsync()
                setOnPreparedListener { start() }
            }
        } catch (e: Exception) {
            speakWithTts(words.getOrNull(position)?.word ?: "", position)
        }
    }


    private fun speakWithTts(word: String, position: Int) {
        if (!ttsReady || tts == null) return

        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word_$position")

        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            if (playingPosition == position) {
                playingPosition = -1
                notifyItemChanged(position)
            }
        }
    }

    private fun initTts(context: Context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }
    }


    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}