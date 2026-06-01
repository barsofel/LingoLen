package ofelya.barseghyan.lingolens

import com.google.mlkit.nl.translate.TranslateLanguage


object AppLanguages {

    data class Language(
        val name: String,
        val code: String,
        val flag: String
    )

    val all = listOf(
        Language("Arabic",     TranslateLanguage.ARABIC,      "🇸🇦"),
        Language("Chinese",    TranslateLanguage.CHINESE,     "🇨🇳"),
        Language("English",    TranslateLanguage.ENGLISH,     "🇬🇧"),
        Language("French",     TranslateLanguage.FRENCH,      "🇫🇷"),
        Language("German",     TranslateLanguage.GERMAN,      "🇩🇪"),
        Language("Hindi",      TranslateLanguage.HINDI,       "🇮🇳"),
        Language("Italian",    TranslateLanguage.ITALIAN,     "🇮🇹"),
        Language("Japanese",   TranslateLanguage.JAPANESE,    "🇯🇵"),
        Language("Korean",     TranslateLanguage.KOREAN,      "🇰🇷"),
        Language("Portuguese", TranslateLanguage.PORTUGUESE,  "🇵🇹"),
        Language("Russian",    TranslateLanguage.RUSSIAN,     "🇷🇺"),
        Language("Spanish",    TranslateLanguage.SPANISH,     "🇪🇸"),
        Language("Turkish",    TranslateLanguage.TURKISH,     "🇹🇷")
    )

    val names: List<String> = all.map { it.name }
    val codes: List<String> = all.map { it.code }

    fun byCode(code: String): Language? = all.find { it.code == code }
    fun byName(name: String): Language? = all.find { it.name == name }
}