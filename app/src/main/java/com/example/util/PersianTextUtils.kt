package com.example.util

/**
 * Shared Persian/Arabic search normalization and numeral conversion utility.
 * Matches desktop Python/JS/Java normalization test vectors:
 * - Strips ZWNJ (\u200C), tatweel (\u0640), and Arabic diacritics (harakat)
 * - Unifies Arabic/Persian Yeh (ي, ی, ى, ئ -> ی) and Kaf (ك, ک -> ک)
 * - Unifies Alef variants (أ, إ, آ, ٱ -> ا) and Heh/Teh Marbuta (ة, ه -> ه)
 * - Converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits to ASCII (0-9) for indexing
 * - Collapses repeated whitespace and lowercases Latin characters
 */
object PersianTextUtils {

    private val DIACRITICS_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640\\u200C\\u200D\\uFEFF]")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun normalizeForSearch(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val sb = StringBuilder(input.length)
        val stripped = DIACRITICS_REGEX.replace(input, "")
        for (ch in stripped) {
            val mapped = when (ch) {
                'ي', 'ى', 'ئ', 'ی', 'ۍ' -> 'ی'
                'ك', 'ک', 'ڪ' -> 'ک'
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة', 'ۀ', 'ه' -> 'ه'
                'ؤ' -> 'و'
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                else -> ch.lowercaseChar()
            }
            sb.append(mapped)
        }
        return WHITESPACE_REGEX.replace(sb.toString().trim(), " ")
    }

    fun formatDigits(input: String, usePersianDigits: Boolean): String {
        if (!usePersianDigits) {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                    in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(('۰'.code + (ch.code - '0'.code)).toChar())
                in '٠'..'٩' -> sb.append(('۰'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumber(value: Int, usePersianDigits: Boolean = true): String =
        formatDigits(value.toString(), usePersianDigits)

    fun formatNumber(value: Long, usePersianDigits: Boolean = true): String =
        formatDigits(value.toString(), usePersianDigits)

    fun optionBadgeLetter(index: Int): String = when (index) {
        0 -> "الف"
        1 -> "ب"
        2 -> "ج"
        3 -> "د"
        4 -> "هـ"
        else -> (index + 1).toString()
    }

    /**
     * BidiGuard: Wraps mixed Persian/number runs with explicit Right-to-Left Mark (\u200F)
     * and Left-to-Right Mark (\u200E) so numbers and Persian labels never invert in PDF or Canvas.
     */
    object BidiGuard {
        const val RLM = "\u200F"
        const val LRM = "\u200E"

        fun wrapRtlLabelWithNumber(labelFa: String, numberStr: String): String {
            return "$RLM$labelFa $LRM$numberStr$RLM"
        }

        fun splitRuns(text: String): List<BidiRun> {
            if (text.isEmpty()) return emptyList()
            val runs = mutableListOf<BidiRun>()
            var currentSb = StringBuilder()
            var currentIsNumericOrLatin = isNumericOrLatin(text[0])

            for (ch in text) {
                val charIsNumLat = isNumericOrLatin(ch)
                if (charIsNumLat == currentIsNumericOrLatin || ch == ' ') {
                    currentSb.append(ch)
                } else {
                    if (currentSb.isNotEmpty()) {
                        runs.add(BidiRun(currentSb.toString().trim(), !currentIsNumericOrLatin))
                    }
                    currentSb = StringBuilder().append(ch)
                    currentIsNumericOrLatin = charIsNumLat
                }
            }
            if (currentSb.isNotEmpty()) {
                runs.add(BidiRun(currentSb.toString().trim(), !currentIsNumericOrLatin))
            }
            return runs.filter { it.text.isNotEmpty() }
        }

        private fun isNumericOrLatin(ch: Char): Boolean {
            return ch in '0'..'9' || ch in '۰'..'۹' || ch in 'a'..'z' || ch in 'A'..'Z' || ch == '%' || ch == '/' || ch == '-'
        }
    }

    data class BidiRun(val text: String, val isRtl: Boolean)
}
