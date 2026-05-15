package ofelya.barseghyan.lingolens

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class RepeatFragment : Fragment() {

    private lateinit var tvCardWord: TextView
    private lateinit var tvCardTranslation: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvHint: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardFlashcard: CardView
    private lateinit var layoutButtons: View
    private lateinit var layoutRepeatEmpty: View

    private val viewModel: WordViewModel by activityViewModels()

    private var words = mutableListOf<WordEntry>()
    private var currentIndex = 0
    private var score = 0
    private var isFlipped = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_repeat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCardWord        = view.findViewById(R.id.tvCardWord)
        tvCardTranslation = view.findViewById(R.id.tvCardTranslation)
        tvProgress        = view.findViewById(R.id.tvProgress)
        tvScore           = view.findViewById(R.id.tvScore)
        tvHint            = view.findViewById(R.id.tvHint)
        progressBar       = view.findViewById(R.id.progressBar)
        cardFlashcard     = view.findViewById(R.id.cardFlashcard)
        layoutButtons     = view.findViewById(R.id.layoutButtons)
        layoutRepeatEmpty = view.findViewById(R.id.layoutRepeatEmpty)

        // Big white font for both card text views
        tvCardWord.textSize = 36f
        tvCardWord.setTextColor(Color.WHITE)

        tvCardTranslation.textSize = 30f
        tvCardTranslation.setTextColor(Color.WHITE)

        loadWords()

        cardFlashcard.setOnClickListener { flipCard() }
        view.findViewById<Button>(R.id.btnCorrect).setOnClickListener { nextCard(correct = true) }
        view.findViewById<Button>(R.id.btnWrong).setOnClickListener { nextCard(correct = false) }
    }

    private fun loadWords() {
        viewModel.filteredWords.observe(viewLifecycleOwner) { dbWords ->
            if (dbWords.isEmpty()) {
                layoutRepeatEmpty.visibility = View.VISIBLE
                cardFlashcard.visibility     = View.GONE
                layoutButtons.visibility     = View.GONE
            } else {
                layoutRepeatEmpty.visibility = View.GONE
                cardFlashcard.visibility     = View.VISIBLE
                words = dbWords.toMutableList()
                words.shuffle()
                currentIndex = 0
                score = 0
                showCard()
            }
        }
    }

    private fun showCard() {
        if (currentIndex >= words.size) {
            showFinished()
            return
        }
        isFlipped = false
        val word = words[currentIndex]

        tvCardWord.text       = word.word
        tvCardWord.visibility = View.VISIBLE

        tvCardTranslation.text  = "tap to reveal"
        tvCardTranslation.alpha = 0.4f

        tvHint.text              = "What does this mean?"
        layoutButtons.visibility = View.GONE

        tvProgress.text      = "Card ${currentIndex + 1} of ${words.size}"
        progressBar.progress = ((currentIndex.toFloat() / words.size) * 100).toInt()
        tvScore.text         = "✓ $score"
    }

    private fun flipCard() {
        if (isFlipped) return
        isFlipped = true

        val word = words[currentIndex]

        // Hide the original word — show only the translation
        tvCardWord.visibility   = View.GONE
        tvCardTranslation.text  = word.translation
        tvCardTranslation.alpha = 1f

        tvHint.text              = "Did you know it?"
        layoutButtons.visibility = View.VISIBLE

        cardFlashcard.animate()
            .scaleX(0f).setDuration(150)
            .withEndAction {
                cardFlashcard.animate().scaleX(1f).setDuration(150).start()
            }.start()
    }

    private fun nextCard(correct: Boolean) {
        if (correct) score++
        currentIndex++
        showCard()
    }

    private fun showFinished() {
        tvCardWord.visibility = View.VISIBLE
        tvCardWord.text       = "Done! 🎉"

        tvCardTranslation.text  = "$score / ${words.size} correct"
        tvCardTranslation.alpha = 1f

        tvHint.text          = "Session complete"
        progressBar.progress = 100
        tvProgress.text      = "Finished"

        layoutButtons.visibility = View.VISIBLE
        requireView().findViewById<Button>(R.id.btnCorrect).apply {
            text = "↺  Restart"
            setOnClickListener {
                currentIndex = 0
                score = 0
                words.shuffle()
                text = "✓  Got it"
                setOnClickListener { nextCard(correct = true) }
                showCard()
            }
        }
        requireView().findViewById<Button>(R.id.btnWrong).visibility = View.GONE
    }
}