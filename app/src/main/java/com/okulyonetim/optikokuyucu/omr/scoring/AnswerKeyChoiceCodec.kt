package com.okulyonetim.optikokuyucu.omr.scoring

/**
 * Compact backward-compatible encoding for one or more accepted choices in an answer key.
 * Existing single-choice keys remain unchanged ("A"); multiple accepted choices are stored
 * as a pipe-delimited value ("A|C").
 */
object AnswerKeyChoiceCodec {
    private const val SEPARATOR = "|"

    fun encode(choices: Iterable<String>): String = choices
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(SEPARATOR)

    fun decode(value: String?): List<String> = value
        ?.split(SEPARATOR)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        .orEmpty()
}
