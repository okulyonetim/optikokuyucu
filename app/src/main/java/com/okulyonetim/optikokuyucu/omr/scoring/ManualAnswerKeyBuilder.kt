package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate

data class ManualAnswerSection(
    val id: String,
    val label: String,
    val questionIds: List<String>,
    val allowedChoices: Set<String>
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(questionIds.isNotEmpty())
        require(allowedChoices.isNotEmpty())
    }
}

object ManualAnswerKeyBuilder {
    fun sections(document: DesignerDocument?, template: OmrTemplate): List<ManualAnswerSection> {
        if (document != null) {
            val groups = document.components.filterIsInstance<QuestionGroupComponent>()
            if (groups.isNotEmpty()) {
                return groups.map { group ->
                    ManualAnswerSection(
                        id = group.id,
                        label = group.label.ifBlank { group.id },
                        questionIds = (0 until group.questionCount).map { index ->
                            DesignerTemplateCompiler.questionReadId(group, group.startQuestion + index)
                        },
                        allowedChoices = group.choices.toSet()
                    )
                }
            }
        }
        return listOf(
            ManualAnswerSection(
                id = "all",
                label = "Tüm Sorular",
                questionIds = template.bubbleRows.map { it.id },
                allowedChoices = template.bubbleRows.flatMap { row -> row.bubbles.map { it.id } }.toSet()
            )
        )
    }

    fun entriesFor(
        answerKey: AnswerKey?,
        sections: List<ManualAnswerSection>
    ): Map<String, String> = sections.associate { section ->
        section.id to if (answerKey == null) {
            ""
        } else {
            section.questionIds.joinToString("") { questionId ->
                answerKey.answers[questionId].orEmpty()
            }
        }
    }

    fun build(
        template: OmrTemplate,
        sections: List<ManualAnswerSection>,
        enteredAnswers: Map<String, String>
    ): AnswerKey {
        val templateRows = template.bubbleRows.associateBy { it.id }
        val answers = linkedMapOf<String, String>()
        sections.forEach { section ->
            val values = parseSequence(enteredAnswers[section.id].orEmpty())
            require(values.size == section.questionIds.size) {
                "${section.label}: ${section.questionIds.size} cevap gerekli, ${values.size} cevap girildi."
            }
            section.questionIds.forEachIndexed { index, questionId ->
                val choice = values[index]
                val allowed = templateRows[questionId]?.bubbles?.map { it.id }.orEmpty()
                require(choice in allowed) {
                    "${section.label}: ${index + 1}. cevap '$choice' geçersiz (${allowed.joinToString("/")})."
                }
                answers[questionId] = choice
            }
        }
        require(answers.keys == templateRows.keys) { "Manuel cevap anahtarı tüm soruları kapsamalıdır." }
        return AnswerKey(template.id, template.version, answers)
    }

    fun parseSequence(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.isBlank()) return emptyList()
        return if (',' in normalized || ' ' in normalized || ';' in normalized) {
            normalized.split(Regex("[,;\\s]+"))
                .map(String::trim)
                .filter(String::isNotBlank)
        } else {
            normalized.codePoints().toArray().map { String(Character.toChars(it)) }
        }
    }
}
