package com.okulyonetim.optikokuyucu.exam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamScoringInferenceTest {
    @Test
    fun `canonical structured 90-question LGS layout is detected`() {
        val questionIds = buildList {
            addQuestions("answers-1", 20)
            addQuestions("answers-2", 10)
            addQuestions("answers-3", 10)
            addQuestions("answers-4", 10)
            addQuestions("answers-5", 20)
            addQuestions("answers-6", 20)
        }

        assertTrue(ExamScoringInference.looksLikeLgsQuestionIds(questionIds))
    }

    @Test
    fun `ordinary 90-question layout is not promoted to LGS`() {
        val questionIds = (1..90).map { "genel:$it" }

        assertFalse(ExamScoringInference.looksLikeLgsQuestionIds(questionIds))
    }

    @Test
    fun `incomplete six-test layout is not promoted to LGS`() {
        val questionIds = buildList {
            addQuestions("answers-1", 20)
            addQuestions("answers-2", 10)
            addQuestions("answers-3", 10)
            addQuestions("answers-4", 10)
            addQuestions("answers-5", 20)
            addQuestions("answers-6", 19)
        }

        assertFalse(ExamScoringInference.looksLikeLgsQuestionIds(questionIds))
    }

    private fun MutableList<String>.addQuestions(prefix: String, count: Int) {
        repeat(count) { index -> add("$prefix:${index + 1}") }
    }
}
