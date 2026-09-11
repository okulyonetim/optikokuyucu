package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.scoring.ExamScore
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPaperPresentationTest {
    private val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = "lgs",
        templateVersion = 1
    )

    @Test
    fun metadataEditorPreservesRawLinkIdentity() {
        val exam = Exam(
            id = "exam",
            name = "LGS",
            schoolName = "Okul",
            templateSelection = selection,
            examDateEpochDay = 1L,
            createdAtEpochMs = 1L,
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "scan-1",
                    studentNumber = "16",
                    bookletCode = "A",
                    linkedAtEpochMs = 77L
                )
            )
        )

        val updated = ExamPaperMetadataEditor.update(
            exam = exam,
            scanRecordId = "scan-1",
            studentName = " FATMA ZEHRA GÜRBÜZ ",
            className = " 8A ",
            studentNumber = " 16 ",
            bookletCode = " B "
        )
        val link = updated.papers.single()

        assertEquals("scan-1", link.scanRecordId)
        assertEquals(77L, link.linkedAtEpochMs)
        assertEquals("FATMA ZEHRA GÜRBÜZ", link.studentName)
        assertEquals("8A", link.className)
        assertEquals("16", link.studentNumber)
        assertEquals("B", link.bookletCode)
    }

    @Test
    fun paperRemovalUnlinksOnlyRequestedScan() {
        val exam = Exam(
            id = "exam",
            name = "LGS",
            schoolName = "Okul",
            templateSelection = selection,
            examDateEpochDay = 1L,
            createdAtEpochMs = 1L,
            papers = listOf(
                ExamPaperLink(scanRecordId = "scan-1", linkedAtEpochMs = 10L),
                ExamPaperLink(scanRecordId = "scan-2", linkedAtEpochMs = 20L)
            )
        )

        val updated = ExamPaperRemoval.unlink(exam, "scan-1")

        assertEquals(listOf("scan-2"), updated.papers.map { it.scanRecordId })
        assertEquals(null, updated.paperForScan("scan-1"))
        assertTrue(runCatching { ExamPaperRemoval.unlink(updated, "missing") }.isFailure)
    }

    @Test
    fun fourWrongPolicyProducesReferenceStyleNet() {
        val evaluations = buildList {
            repeat(12) { index -> add(eval("q$index", QuestionEvaluationState.CORRECT, 1.0)) }
            repeat(8) { index -> add(eval("w$index", QuestionEvaluationState.WRONG, -0.25)) }
            repeat(3) { index -> add(eval("b$index", QuestionEvaluationState.BLANK, 0.0)) }
        }
        val metrics = ExamPaperMetrics.from(ExamScore(evaluations))

        assertEquals(12, metrics.correct)
        assertEquals(8, metrics.wrong)
        assertEquals(3, metrics.blank)
        assertEquals(10.0, metrics.net, 0.0001)
    }

    @Test
    fun normalScoringScalesNetToConfiguredRange() {
        val exam = scoringExam(
            configuration = ExamScoringConfiguration(),
            wrongPolicy = WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT
        )
        val evaluations = buildList {
            repeat(12) { index -> add(eval("q$index", QuestionEvaluationState.CORRECT, 0.0)) }
            repeat(8) { index -> add(eval("w$index", QuestionEvaluationState.WRONG, 0.0)) }
        }

        val result = ExamScoreEngine.calculate(
            exam,
            listOf(ExamPaperScoreInput("paper", ExamScore(evaluations)))
        ).getValue("paper")

        assertEquals(10.0, result.net, 0.0001)
        assertEquals(50.0, requireNotNull(result.calculatedScore), 0.0001)
        assertEquals(100.0, requireNotNull(result.maximumScore), 0.0001)
        assertEquals(ExamCalculatedScoreScope.SCALED, result.scope)
    }

    @Test
    fun customScoringUsesCustomWrongDivisorAndLessonWeights() {
        val exam = scoringExam(
            configuration = ExamScoringConfiguration(
                type = ExamScoringType.CUSTOM,
                customWrongAnswerDivisor = 4.0,
                lessonWeights = linkedMapOf("turkce" to 1.0, "matematik" to 3.0)
            )
        )
        val evaluations = buildList {
            repeat(10) { index -> add(eval("turkce:${index + 1}", QuestionEvaluationState.CORRECT, 0.0)) }
            repeat(5) { index -> add(eval("matematik:${index + 1}", QuestionEvaluationState.CORRECT, 0.0)) }
            repeat(5) { index -> add(eval("matematik:${index + 6}", QuestionEvaluationState.WRONG, 0.0)) }
        }

        val result = ExamScoreEngine.calculate(
            exam,
            listOf(ExamPaperScoreInput("paper", ExamScore(evaluations)))
        ).getValue("paper")

        assertEquals(13.75, result.net, 0.0001)
        assertEquals(53.125, requireNotNull(result.calculatedScore), 0.0001)
        assertEquals(3.0, result.lessons.first { it.lessonId == "matematik" }.weight, 0.0)
    }

    @Test
    fun lgsUsesOfficialLessonWeightsAndLocalCohortMebMethod() {
        val exam = scoringExam(
            configuration = ExamScoringConfiguration.forType(ExamScoringType.LGS)
        )
        val papers = listOf(
            mebPaper("low", LGS_LESSONS, correctPerLesson = 1),
            mebPaper("mid", LGS_LESSONS, correctPerLesson = 2),
            mebPaper("high", LGS_LESSONS, correctPerLesson = 3)
        )

        val results = ExamScoreEngine.calculate(exam, papers)

        assertEquals(100.0, requireNotNull(results.getValue("low").calculatedScore), 0.0001)
        assertEquals(300.0, requireNotNull(results.getValue("mid").calculatedScore), 0.0001)
        assertEquals(500.0, requireNotNull(results.getValue("high").calculatedScore), 0.0001)
        assertEquals(4.0, results.getValue("mid").lessons.first { it.lessonId == "turkce" }.weight, 0.0)
        assertEquals(4.0, results.getValue("mid").lessons.first { it.lessonId == "matematik" }.weight, 0.0)
        assertEquals(1.0, results.getValue("mid").lessons.first { it.lessonId == "din" }.weight, 0.0)
        assertEquals(ExamCalculatedScoreScope.LOCAL_COHORT_MEB_METHOD, results.getValue("mid").scope)
        assertTrue(results.getValue("mid").note.contains("resmî ulusal sonuç değildir"))
    }

    @Test
    fun lgsMapsStructuredAnswerGroupIdsToMebLessons() {
        val exam = scoringExam(
            configuration = ExamScoringConfiguration.forType(ExamScoringType.LGS)
        )
        val papers = listOf(
            structuredLgsPaper("low", correctPerLesson = 1),
            structuredLgsPaper("mid", correctPerLesson = 2),
            structuredLgsPaper("high", correctPerLesson = 3)
        )

        val results = ExamScoreEngine.calculate(exam, papers)
        val mid = results.getValue("mid")

        assertEquals(300.0, requireNotNull(mid.calculatedScore), 0.0001)
        assertEquals(
            listOf("turkce", "matematik", "fen", "inkilap", "din", "yabanci"),
            mid.lessons.map { it.lessonId }
        )
        assertEquals(ExamCalculatedScoreScope.LOCAL_COHORT_MEB_METHOD, mid.scope)
    }

    @Test
    fun iokbsUsesThreePointWeightsForAllFourTests() {
        val exam = scoringExam(
            configuration = ExamScoringConfiguration.forType(ExamScoringType.IOKBS)
        )
        val papers = listOf(
            mebPaper("low", IOKBS_LESSONS, correctPerLesson = 1),
            mebPaper("mid", IOKBS_LESSONS, correctPerLesson = 2),
            mebPaper("high", IOKBS_LESSONS, correctPerLesson = 3)
        )

        val mid = ExamScoreEngine.calculate(exam, papers).getValue("mid")

        assertEquals(300.0, requireNotNull(mid.calculatedScore), 0.0001)
        assertTrue(mid.lessons.all { it.weight == 3.0 })
    }

    @Test
    fun lgsDoesNotInventScoreWhenRequiredTestIsMissing() {
        val exam = scoringExam(
            configuration = ExamScoringConfiguration.forType(ExamScoringType.LGS)
        )
        val incompleteLessons = LGS_LESSONS - "yabanci"
        val papers = listOf(
            mebPaper("a", incompleteLessons, correctPerLesson = 1),
            mebPaper("b", incompleteLessons, correctPerLesson = 2)
        )

        val result = ExamScoreEngine.calculate(exam, papers).getValue("a")

        assertNull(result.calculatedScore)
        assertEquals(ExamCalculatedScoreScope.UNAVAILABLE, result.scope)
        assertTrue(result.note.contains("eksik test bölümü"))
    }

    @Test
    fun lessonPrefixAndQuestionNumberAreStable() {
        assertEquals("turkce", questionLessonPrefix("turkce:20"))
        assertEquals("20", questionDisplayNumber("turkce:20"))
        assertEquals(null, questionLessonPrefix("7"))
        assertEquals("7", questionDisplayNumber("7"))
    }

    private fun scoringExam(
        configuration: ExamScoringConfiguration,
        wrongPolicy: WrongAnswerPolicy = WrongAnswerPolicy.KEEP_AS_IS
    ): Exam = Exam(
        id = "exam-score",
        name = "Deneme",
        schoolName = "Okul",
        templateSelection = selection,
        wrongAnswerPolicy = wrongPolicy,
        scoringConfiguration = configuration,
        examDateEpochDay = 1L,
        createdAtEpochMs = 1L
    )

    private fun mebPaper(
        id: String,
        lessons: List<String>,
        correctPerLesson: Int
    ): ExamPaperScoreInput {
        val evaluations = buildList {
            lessons.forEach { lesson ->
                repeat(4) { index ->
                    add(
                        eval(
                            "$lesson:${index + 1}",
                            if (index < correctPerLesson) {
                                QuestionEvaluationState.CORRECT
                            } else {
                                QuestionEvaluationState.BLANK
                            },
                            0.0
                        )
                    )
                }
            }
        }
        return ExamPaperScoreInput(id, ExamScore(evaluations))
    }

    private fun structuredLgsPaper(
        id: String,
        correctPerLesson: Int
    ): ExamPaperScoreInput {
        val groups = listOf("answers-1", "answers-2", "answers-3", "answers-4", "answers-5", "answers-6")
        val evaluations = buildList {
            groups.forEach { group ->
                repeat(4) { index ->
                    add(
                        eval(
                            "$group:${index + 1}",
                            if (index < correctPerLesson) QuestionEvaluationState.CORRECT else QuestionEvaluationState.BLANK,
                            0.0
                        )
                    )
                }
            }
        }
        return ExamPaperScoreInput(id, ExamScore(evaluations))
    }

    private fun eval(id: String, state: QuestionEvaluationState, points: Double) =
        QuestionEvaluation(
            questionId = id,
            state = state,
            expectedChoice = "A",
            selectedChoice = if (state == QuestionEvaluationState.BLANK) null else "A",
            recognitionConfidence = 1.0,
            points = points
        )

    private companion object {
        val LGS_LESSONS = listOf("turkce", "matematik", "fen", "inkilap", "din", "yabanci")
        val IOKBS_LESSONS = listOf("turkce", "matematik", "fen", "sosyal")
    }
}
