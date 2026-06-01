package ofelya.barseghyan.lingolens

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class RepeatFragment : Fragment() {

    enum class QuizMode { MULTIPLE_CHOICE, SPELLING, TAP_AND_REVEAL }
    data class Question(val word: WordEntry, val mode: QuizMode)

    private lateinit var layoutRepeatEmpty: View
    private lateinit var layoutLearnContent: ScrollView
    private lateinit var layoutComplete: LinearLayout

    private lateinit var tvRound: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView

    private lateinit var cardQuestion: CardView
    private lateinit var tvQuestionLabel: TextView
    private lateinit var tvPrompt: TextView
    private lateinit var tvTapToRevealHint: TextView

    private lateinit var layoutMultipleChoice: LinearLayout
    private lateinit var mcButtons: List<Button>

    private lateinit var layoutSpelling: LinearLayout
    private lateinit var etSpelling: EditText
    private lateinit var btnCheck: Button
    private lateinit var tvSpellingFeedback: TextView

    private lateinit var layoutTapRevealControls: LinearLayout
    private lateinit var btnTapRevealNext: Button

    private lateinit var tvCompleteEmoji: TextView
    private lateinit var tvCompleteTitle: TextView
    private lateinit var tvCompleteSubtitle: TextView
    private lateinit var btnRestart: Button
    private lateinit var btnEmptyRestart: Button
    private lateinit var btnNotSure: Button
    private val viewModel: WordViewModel by activityViewModels()

    private var allWords = listOf<WordEntry>()
    private var currentQueue = mutableListOf<Question>()
    private var wrongWords = linkedSetOf<WordEntry>()
    private var currentIndex = 0
    private var correctCount = 0
    private var round = 1
    private var answered = false

    private var isCardFlipped = false
    private var trueAnswerString = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_repeat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGlobalHeader(view, "LingoLens")

        bindViews(view)
        observeWords()
    }

    private fun bindViews(v: View) {
        layoutRepeatEmpty = v.findViewById(R.id.layoutRepeatEmpty)
        layoutLearnContent = v.findViewById(R.id.layoutLearnContent)
        layoutComplete = v.findViewById(R.id.layoutComplete)

        tvRound = v.findViewById(R.id.tvRound)
        progressBar = v.findViewById(R.id.progressBar)
        tvProgress = v.findViewById(R.id.tvProgress)

        cardQuestion = v.findViewById(R.id.cardQuestion)
        tvQuestionLabel = v.findViewById(R.id.tvQuestionLabel)
        tvPrompt = v.findViewById(R.id.tvPrompt)
        tvTapToRevealHint = v.findViewById(R.id.tvTapToRevealHint)
        btnTapRevealNext = v.findViewById(R.id.btnTapRevealNext)
        btnNotSure       = v.findViewById(R.id.btnNotSure)

        layoutMultipleChoice = v.findViewById(R.id.layoutMultipleChoice)
        mcButtons = listOf(
            v.findViewById(R.id.btnOption1),
            v.findViewById(R.id.btnOption2),
            v.findViewById(R.id.btnOption3),
            v.findViewById(R.id.btnOption4)
        )

        layoutSpelling = v.findViewById(R.id.layoutSpelling)
        etSpelling = v.findViewById(R.id.etSpelling)
        btnCheck = v.findViewById(R.id.btnCheck)
        tvSpellingFeedback = v.findViewById(R.id.tvSpellingFeedback)

        layoutTapRevealControls = v.findViewById(R.id.layoutTapRevealControls)
        btnTapRevealNext = v.findViewById(R.id.btnTapRevealNext)

        tvCompleteEmoji = v.findViewById(R.id.tvCompleteEmoji)
        tvCompleteTitle = v.findViewById(R.id.tvCompleteTitle)
        tvCompleteSubtitle = v.findViewById(R.id.tvCompleteSubtitle)
        btnRestart = v.findViewById(R.id.btnRestart)
        btnEmptyRestart = v.findViewById(R.id.btnEmptyRestart)

        btnCheck.setOnClickListener { checkSpelling() }
        btnRestart.setOnClickListener { restartSession() }
        btnEmptyRestart.setOnClickListener { restartSession() }

        btnTapRevealNext.setOnClickListener {
            correctCount++
            nextQuestion()
        }

        btnNotSure.setOnClickListener {
            val word = currentQueue[currentIndex].word
            wrongWords.add(word)
            currentQueue.add(currentQueue[currentIndex])
            nextQuestion()
        }

        cardQuestion.setOnClickListener {
            if (currentQueue.isNotEmpty() && currentIndex < currentQueue.size &&
                currentQueue[currentIndex].mode == QuizMode.TAP_AND_REVEAL) {
                executeCardFlipTransition()
            }
        }

        etSpelling.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkSpelling(); true
            } else false
        }
    }

    private fun observeWords() {
        viewModel.filteredWords.observe(viewLifecycleOwner) { words ->
            if (words.isNullOrEmpty()) {
                showEmpty()
            } else {
                allWords = words
                startSession()
            }
        }
    }

    private fun startSession() {
        round = 1
        correctCount = 0
        wrongWords.clear()

        if (allWords.isEmpty()) {
            showEmpty()
            return
        }

        buildQueue(allWords)
        showLearnContent()
        showQuestion()
    }

    private fun restartSession() {
        startSession()
    }

    private fun buildQueue(words: Collection<WordEntry>) {
        currentQueue = words.flatMap { word ->
            listOf(
                Question(word, QuizMode.TAP_AND_REVEAL),
                Question(word, QuizMode.MULTIPLE_CHOICE),
                Question(word, QuizMode.SPELLING)
            )
        }.shuffled().toMutableList()

        currentIndex = 0
    }

    private fun showQuestion() {
        if (currentIndex >= currentQueue.size) {
            if (wrongWords.isEmpty()) {
                showComplete()
            } else {
                round++
                val retryWords = wrongWords.toList()
                wrongWords.clear()
                buildQueue(retryWords)
                showQuestion()
            }
            return
        }

        answered = false
        isCardFlipped = false
        cardQuestion.rotationY = 0f

        val q = currentQueue[currentIndex]
        updateProgress()

        cardQuestion.animate()
            .alpha(0f)
            .setDuration(100)
            .withEndAction {
                when (q.mode) {
                    QuizMode.TAP_AND_REVEAL -> setupTapAndRevealStage(q.word)
                    QuizMode.MULTIPLE_CHOICE -> setupMultipleChoiceStage(q.word)
                    QuizMode.SPELLING -> setupSpellingStage(q.word)
                }
                cardQuestion.animate().alpha(1f).setDuration(150).start()
            }
            .start()
    }

    private fun setupTapAndRevealStage(word: WordEntry) {
        hideKeyboard()
        tvQuestionLabel.text = "Remember the translation"
        tvPrompt.text = word.word
        trueAnswerString = word.translation

        tvTapToRevealHint.visibility = View.VISIBLE
        layoutMultipleChoice.visibility = View.GONE
        layoutSpelling.visibility = View.GONE
        layoutTapRevealControls.visibility = View.GONE
    }

    private fun executeCardFlipTransition() {
        if (isCardFlipped) return
        isCardFlipped = true

        val flipStage1 = ObjectAnimator.ofFloat(cardQuestion, "rotationY", 0f, 90f).setDuration(150)
        val flipStage2 = ObjectAnimator.ofFloat(cardQuestion, "rotationY", -90f, 0f).setDuration(150)

        flipStage1.interpolator = AccelerateDecelerateInterpolator()
        flipStage2.interpolator = AccelerateDecelerateInterpolator()

        flipStage1.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                tvQuestionLabel.text = "Translation"
                tvPrompt.text = trueAnswerString
                tvTapToRevealHint.visibility = View.GONE
                layoutTapRevealControls.visibility = View.VISIBLE
                flipStage2.start()
            }
        })
        flipStage1.start()
    }

    private fun setupMultipleChoiceStage(word: WordEntry) {
        hideKeyboard()
        tvQuestionLabel.text = "Choose the correct translation"
        tvPrompt.text = word.word
        tvTapToRevealHint.visibility = View.GONE

        layoutMultipleChoice.visibility = View.VISIBLE
        layoutSpelling.visibility = View.GONE
        layoutTapRevealControls.visibility = View.GONE

        val correctTranslation = word.translation
        val distractors = allWords
            .filter { it.translation != correctTranslation }
            .map { it.translation }
            .shuffled()
            .take(3)

        val options = (listOf(correctTranslation) + distractors).shuffled()

        mcButtons.forEachIndexed { i, btn ->
            if (i < options.size) {
                btn.visibility = View.VISIBLE
                btn.text = options[i]
                btn.isEnabled = true
                resetMcButton(btn)
                btn.setOnClickListener {
                    if (!answered) onMcPicked(btn, options[i], correctTranslation, word)
                }
            } else {
                btn.visibility = View.GONE
            }
        }
    }

    private fun onMcPicked(
        clicked: Button,
        selected: String,
        correct: String,
        word: WordEntry
    ) {
        answered = true
        mcButtons.forEach { it.isEnabled = false }

        if (selected == correct) {
            correctCount++
            animateButtonResult(clicked, true)
            clicked.postDelayed({ nextQuestion() }, 700)
        } else {
            animateButtonResult(clicked, false)
            mcButtons.find { it.text == correct }?.let {
                animateButtonResult(it, true)
            }

            wrongWords.add(word)

            currentQueue.getOrNull(currentIndex)?.let { currentQuestion ->
                currentQueue.add(currentQuestion)
            }

            clicked.postDelayed({ nextQuestion() }, 1400)
        }
    }
    private fun setupSpellingStage(word: WordEntry) {
        tvQuestionLabel.text = "Type the word"
        tvPrompt.text = word.translation
        tvTapToRevealHint.visibility = View.GONE

        layoutMultipleChoice.visibility = View.GONE
        layoutSpelling.visibility = View.VISIBLE
        layoutTapRevealControls.visibility = View.GONE

        etSpelling.text?.clear()
        etSpelling.isEnabled = true
        tvSpellingFeedback.visibility = View.GONE
        btnCheck.isEnabled = true

        etSpelling.requestFocus()
        val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etSpelling, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun checkSpelling() {
        if (answered) return

        val typed = etSpelling.text.toString().trim()
        if (typed.isEmpty()) return

        val word = currentQueue[currentIndex].word
        answered = true

        etSpelling.isEnabled = false
        btnCheck.isEnabled = false
        tvSpellingFeedback.visibility = View.VISIBLE

        if (typed.equals(word.word, ignoreCase = true)) {
            correctCount++
            tvSpellingFeedback.text = "✓ Correct!"
            tvSpellingFeedback.setTextColor(Color.parseColor("#00F5D4"))
            view?.postDelayed({ nextQuestion() }, 800)
        } else {
            wrongWords.add(word)
            tvSpellingFeedback.text = "✗ Answer: ${word.word}"
            tvSpellingFeedback.setTextColor(Color.parseColor("#FF4757"))
            view?.postDelayed({ nextQuestion() }, 1500)
        }
    }

    private fun nextQuestion() {
        currentIndex++
        showQuestion()
    }

    private fun updateProgress() {
        val total = currentQueue.size
        tvRound.text = if (round == 1) "Learn" else "Round $round"
        tvProgress.text = "${currentIndex + 1} / $total"
        progressBar.max = total
        progressBar.progress = currentIndex + 1
    }

    private fun showEmpty() {
        hideKeyboard()
        layoutRepeatEmpty.visibility = View.VISIBLE
        layoutLearnContent.visibility = View.GONE
        layoutComplete.visibility = View.GONE
    }

    private fun showLearnContent() {
        layoutRepeatEmpty.visibility = View.GONE
        layoutLearnContent.visibility = View.VISIBLE
        layoutComplete.visibility = View.GONE
    }

    private fun showComplete() {
        hideKeyboard()
        layoutRepeatEmpty.visibility = View.GONE
        layoutLearnContent.visibility = View.GONE
        layoutComplete.visibility = View.VISIBLE

        tvCompleteSubtitle.text = "$correctCount correct stages across $round round(s)"

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tvCompleteEmoji, "scaleX", 0f, 1.2f, 1f),
                ObjectAnimator.ofFloat(tvCompleteEmoji, "scaleY", 0f, 1.2f, 1f)
            )
            duration = 500
            start()
        }
    }

    private fun resetMcButton(btn: Button) {
        btn.setBackgroundColor(Color.parseColor("#24243E"))
        btn.setTextColor(Color.WHITE)
    }

    private fun animateButtonResult(btn: Button, isCorrect: Boolean) {
        btn.setBackgroundColor(Color.parseColor(if (isCorrect) "#00F5D4" else "#FF4757"))
        btn.setTextColor(if (isCorrect) Color.parseColor("#0F0F1A") else Color.WHITE)
    }

    private fun hideKeyboard() {
        val view = activity?.currentFocus
        if (view != null) {
            val imm = activity?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}