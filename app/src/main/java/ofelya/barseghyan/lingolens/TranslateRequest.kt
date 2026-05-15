package ofelya.barseghyan.lingolens

data class TranslateRequest(
    val q: String,
    val source: String,
    val target: String,
    val format: String = "text"
)