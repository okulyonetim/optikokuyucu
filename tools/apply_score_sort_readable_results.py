from pathlib import Path


def replace(path, old, new, count=1):
    p = Path(path)
    text = p.read_text()
    actual = text.count(old)
    if actual < count:
        raise SystemExit(f"Expected at least {count} matches in {path}, found {actual}: {old[:100]!r}")
    p.write_text(text.replace(old, new, count))


def replace_all(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"No match in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new))


# 1) A student double mark is an ordinary wrong answer. Multi-answer keys still accept either choice.
scoring = 'app/src/main/java/com/okulyonetim/optikokuyucu/omr/scoring/OmrScoring.kt'
replace(scoring,
    ' * selecting any one accepted choice is correct; a student DOUBLE_MARK remains a double mark.\n',
    ' * selecting any one accepted choice is correct. A student marking more than one choice is wrong.\n')
replace(scoring,
'''            ScorableState.DOUBLE_MARK -> QuestionEvaluation(
                questionId = questionId,
                state = QuestionEvaluationState.DOUBLE_MARK,
                expectedChoice = expected,
                selectedChoice = null,
                recognitionConfidence = confidence,
                points = policy.doubleMarkPoints
            )''',
'''            ScorableState.DOUBLE_MARK -> QuestionEvaluation(
                questionId = questionId,
                state = QuestionEvaluationState.WRONG,
                expectedChoice = expected,
                selectedChoice = null,
                recognitionConfidence = confidence,
                points = policy.wrongPoints
            )''')

tests = 'app/src/test/java/com/okulyonetim/optikokuyucu/omr/scoring/OmrScoringTest.kt'
replace(tests,
    '        assertEquals(1, score.wrongCount)\n        assertEquals(1, score.blankCount)\n        assertEquals(1, score.doubleMarkCount)\n',
    '        assertEquals(2, score.wrongCount)\n        assertEquals(1, score.blankCount)\n        assertEquals(0, score.doubleMarkCount)\n')
replace(tests, '        assertEquals(3.0, score.totalPoints, 0.001)\n', '        assertEquals(2.0, score.totalPoints, 0.001)\n')
replace(tests,
'''    fun `student double mark remains double even when key accepts two choices`() {
        val read = BubbleReadResult(
            listOf(q("1", QuestionState.DOUBLE_MARK, null, 0.90))
        )
        val key = AnswerKey("test", 1, mapOf("1" to "A|C"))

        val score = OmrScorer.score(read, key)

        assertEquals(1, score.doubleMarkCount)
        assertEquals(0, score.correctCount)
    }''',
'''    fun `student double mark is wrong even when key accepts two choices`() {
        val read = BubbleReadResult(
            listOf(q("1", QuestionState.DOUBLE_MARK, null, 0.90))
        )
        val key = AnswerKey("test", 1, mapOf("1" to "A|C"))

        val score = OmrScorer.score(
            read,
            key,
            ScoringPolicy(correctPoints = 1.0, wrongPoints = -1.0)
        )

        assertEquals(1, score.wrongCount)
        assertEquals(0, score.doubleMarkCount)
        assertEquals(0, score.correctCount)
        assertEquals(-1.0, score.totalPoints, 0.001)
    }''')

# 2) Exam paper list: score descending, then net/correct, with unscored papers last.
detail = 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/ExamDetailScreen.kt'
replace(detail,
'''    val classes = current.papers.map { paperClass(it, scans[it.scanRecordId]) }
''',
'''    val examReport = remember(current, scans, keys) {
        ExamReportBuilder.build(
            exam = current,
            records = scans.values.toList(),
            answerKeys = keys
        )
    }
    val examRowsByScan = examReport.rows.associateBy { it.scanRecordId }
    val classes = current.papers.map { paperClass(it, scans[it.scanRecordId]) }
''')
replace(detail,
'''    val visiblePapers = current.papers.filter { link ->
        val record = scans[link.scanRecordId]
        val name = link.studentName
        val number = paperNumber(link, record)
        val clazz = paperClass(link, record)
        (normalizedQuery.isBlank() ||
            name.lowercase().contains(normalizedQuery) ||
            number.lowercase().contains(normalizedQuery) ||
            clazz.lowercase().contains(normalizedQuery)) &&
            (classFilter == null || clazz == classFilter)
    }
    val answerKeyCount = keys.count { keyMatchesExam(it, current) }
    val examReport = remember(current, scans, keys) {
        ExamReportBuilder.build(
            exam = current,
            records = scans.values.toList(),
            answerKeys = keys
        )
    }
''',
'''    val visiblePapers = current.papers
        .filter { link ->
            val record = scans[link.scanRecordId]
            val name = link.studentName
            val number = paperNumber(link, record)
            val clazz = paperClass(link, record)
            (normalizedQuery.isBlank() ||
                name.lowercase().contains(normalizedQuery) ||
                number.lowercase().contains(normalizedQuery) ||
                clazz.lowercase().contains(normalizedQuery)) &&
                (classFilter == null || clazz == classFilter)
        }
        .sortedWith(
            compareByDescending<ExamPaperLink> {
                examRowsByScan[it.scanRecordId]?.points ?: Double.NEGATIVE_INFINITY
            }
                .thenByDescending { examRowsByScan[it.scanRecordId]?.net ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { examRowsByScan[it.scanRecordId]?.correct ?: Int.MIN_VALUE }
                .thenBy { it.studentName.lowercase(Locale.forLanguageTag("tr-TR")) }
        )
    val answerKeyCount = keys.count { keyMatchesExam(it, current) }
''')
replace(detail,
    'modifier = Modifier.fillMaxSize().padding(innerPadding),\n            verticalArrangement = Arrangement.spacedBy(7.dp)',
    'modifier = Modifier.fillMaxSize().padding(innerPadding).padding(top = 10.dp),\n            verticalArrangement = Arrangement.spacedBy(8.dp)')

# 3) Student detail: more breathing room below header.
paper_detail = 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/StudentPaperDetailScreen.kt'
replace(paper_detail,
    '        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {',
    '        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(top = 8.dp)) {')
replace(paper_detail,
    'modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),',
    'modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),')

# 4) Result dashboard: larger, clearer typography and spacing.
dashboard = 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/StudentResultDashboard.kt'
for old, new in {
    'shape = RoundedCornerShape(18.dp),': 'shape = RoundedCornerShape(22.dp),',
    'modifier = Modifier.fillMaxWidth().padding(14.dp),\n            verticalArrangement = Arrangement.spacedBy(10.dp)': 'modifier = Modifier.fillMaxWidth().padding(18.dp),\n            verticalArrangement = Arrangement.spacedBy(13.dp)',
    'fontSize = 10.sp,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant': 'fontSize = 12.sp,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant',
    'fontSize = 24.sp,': 'fontSize = 30.sp,',
    'Text("Toplam Net", fontSize = 10.sp': 'Text("Toplam Net", fontSize = 12.sp',
    'fontSize = 20.sp,': 'fontSize = 25.sp,',
    'fontSize = 9.sp,\n                    lineHeight = 12.sp,': 'fontSize = 11.sp,\n                    lineHeight = 15.sp,',
    'Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)': 'Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)',
    'Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)': 'Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)',
    'Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)': 'Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)',
    'Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)': 'Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)',
    '"Ders Sonuçları",\n                modifier = Modifier.padding(horizontal = 12.dp),\n                fontSize = 13.sp,': '"Ders Sonuçları",\n                modifier = Modifier.padding(horizontal = 14.dp),\n                fontSize = 16.sp,',
    'fontSize = if (header) 9.sp else 10.sp,': 'fontSize = if (header) 11.sp else 12.sp,',
    'fontSize = 9.sp,\n        fontWeight = fontWeight,': 'fontSize = 11.sp,\n        fontWeight = fontWeight,',
    'Text("Ders Başarı Grafiği", fontSize = 13.sp': 'Text("Ders Başarı Grafiği", fontSize = 16.sp',
    'Text("Doğru ve yanlış yüzdeleri", fontSize = 9.sp': 'Text("Doğru ve yanlış yüzdeleri", fontSize = 11.sp',
    'Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)': 'Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)'
}.items():
    replace_all(dashboard, old, new)

# 5) Mini answer key: top inset + stronger, consistent exam cards.
mini = 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/MiniAnswerKeyScreen.kt'
replace(mini, 'import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.clickable\n')
replace(mini,
    'modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),\n            verticalArrangement = Arrangement.spacedBy(9.dp)',
    'modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(top = 12.dp),\n            verticalArrangement = Arrangement.spacedBy(11.dp)')
replace(mini, 'Text("Sınav seçin", fontSize = 16.sp, fontWeight = FontWeight.Bold)',
              'Text("Sınav seçin", fontSize = 20.sp, fontWeight = FontWeight.Bold)')
replace(mini,
    'fontSize = 10.sp,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant\n                )\n            }',
    'fontSize = 11.sp,\n                    lineHeight = 16.sp,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant\n                )\n            }')
replace(mini,
'''                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.id == selectedExamId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )''',
'''                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (item.id == selectedExamId) MaterialTheme.colorScheme.primary
                            else productAccentColor(item.name).copy(alpha = 0.55f)
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.id == selectedExamId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)''')
replace(mini, 'modifier = Modifier.fillMaxWidth().padding(11.dp),', 'modifier = Modifier.fillMaxWidth().padding(16.dp),')
replace(mini, 'Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold',
              'Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold')
replace(mini, 'Text("${item.papers.size} kağıt", fontSize = 9.sp,',
              'Text("${item.papers.size} kağıt", fontSize = 11.sp,')

# 6) Report builder: top inset on every step and coherent selection cards.
report_ui = 'app/src/main/java/com/okulyonetim/optikokuyucu/ui/ReportBuilderScreen.kt'
replace(report_ui, 'import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.clickable\n')
replace_all(report_ui,
    'modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),',
    'modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(top = 12.dp),')
replace(report_ui, 'Text("1. Sınav Seçimi", fontSize = 18.sp, fontWeight = FontWeight.Bold)',
                   'Text("1. Sınav Seçimi", fontSize = 20.sp, fontWeight = FontWeight.Bold)')
replace(report_ui, 'Text("Rapor oluşturulacak sınavı seçin.", fontSize = 10.sp,',
                   'Text("Rapor oluşturulacak sınavı seçin.", fontSize = 11.sp,')
replace(report_ui,
'''                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (exam.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )''',
'''                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (exam.id == selectedId) MaterialTheme.colorScheme.primary
                        else productAccentColor(exam.name).copy(alpha = 0.55f)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (exam.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)''')
replace(report_ui, 'modifier = Modifier.fillMaxWidth().padding(12.dp),',
                   'modifier = Modifier.fillMaxWidth().padding(16.dp),')
replace(report_ui, 'Text(exam.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold',
                   'Text(exam.name, fontSize = 15.sp, fontWeight = FontWeight.Bold')
replace(report_ui, 'Text("${exam.papers.size} kağıt · ${exam.schoolName}", fontSize = 9.sp,',
                   'Text("${exam.papers.size} kağıt · ${exam.schoolName}", fontSize = 11.sp,')
replace(report_ui,
'''                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )''',
'''                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.25.dp,
                    if (index == selectedIndex) MaterialTheme.colorScheme.primary
                    else productAccentColor(choice.first).copy(alpha = 0.45f)
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)''')
replace(report_ui, 'Text(choice.first, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)',
                   'Text(choice.first, fontSize = 16.sp, fontWeight = FontWeight.Bold)')
replace(report_ui, 'Text(choice.second, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)',
                   'Text(choice.second, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)')

# 7) Version bump.
gradle = 'app/build.gradle.kts'
replace(gradle, 'versionCode = 122', 'versionCode = 123')
replace(gradle, 'versionName = "0.19.66"', 'versionName = "0.19.67"')
