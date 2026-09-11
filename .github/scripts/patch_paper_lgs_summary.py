from pathlib import Path

exam = Path('app/src/main/java/com/okulyonetim/optikokuyucu/ui/ExamDetailScreen.kt')
text = exam.read_text(encoding='utf-8')

old_call = '''                                ExamPaperCard(\n                                    exam = current,\n                                    link = link,\n                                    record = scans[link.scanRecordId],\n                                    keys = keys,\n                                    onClick = { onOpenPaper(link.scanRecordId) }\n                                )\n'''
new_call = '''                                ExamPaperCard(\n                                    exam = current,\n                                    link = link,\n                                    record = scans[link.scanRecordId],\n                                    keys = keys,\n                                    reportRow = examReport.rows.firstOrNull { it.scanRecordId == link.scanRecordId },\n                                    scoringType = examReport.scoringType,\n                                    onClick = { onOpenPaper(link.scanRecordId) }\n                                )\n'''
if old_call not in text:
    raise SystemExit('ExamPaperCard call not found')
text = text.replace(old_call, new_call, 1)

old_sig = '''private fun ExamPaperCard(\n    exam: Exam,\n    link: ExamPaperLink,\n    record: ScanRecord?,\n    keys: List<StoredAnswerKey>,\n    onClick: () -> Unit\n) {\n'''
new_sig = '''private fun ExamPaperCard(\n    exam: Exam,\n    link: ExamPaperLink,\n    record: ScanRecord?,\n    keys: List<StoredAnswerKey>,\n    reportRow: ExamReportRow?,\n    scoringType: ExamScoringType,\n    onClick: () -> Unit\n) {\n'''
if old_sig not in text:
    raise SystemExit('ExamPaperCard signature not found')
text = text.replace(old_sig, new_sig, 1)

old_when = '''            when {\n                record == null -> ProductStatusBadge("KAYIT YOK", ProductBadgeTone.RED)\n                score == null -> ProductStatusBadge("ANAHTAR YOK", ProductBadgeTone.ORANGE)\n                else -> ProductStatusBadge(\n                    text = formatScore(score.totalPoints),\n                    tone = if (score.confidentlyEvaluated) ProductBadgeTone.GREEN else ProductBadgeTone.ORANGE\n                )\n            }\n'''
new_when = '''            when {\n                record == null -> ProductStatusBadge("KAYIT YOK", ProductBadgeTone.RED)\n                score == null -> ProductStatusBadge("ANAHTAR YOK", ProductBadgeTone.ORANGE)\n                else -> ExamPaperCompactResult(\n                    row = reportRow,\n                    scoringType = scoringType,\n                    confident = score.confidentlyEvaluated\n                )\n            }\n'''
if old_when not in text:
    raise SystemExit('ExamPaperCard result badge block not found')
text = text.replace(old_when, new_when, 1)

insert_after = '''}\n\n@Composable\nprivate fun ExamReportsTab(report: ExamReport, onOpenReports: () -> Unit) {\n'''
new_block = '''}\n\n@Composable\nprivate fun ExamPaperCompactResult(\n    row: ExamReportRow?,\n    scoringType: ExamScoringType,\n    confident: Boolean\n) {\n    val scoreLabel = when (scoringType) {\n        ExamScoringType.LGS -> "LGS"\n        ExamScoringType.IOKBS -> "İOKBS"\n        else -> "Puan"\n    }\n    val accent = if (confident) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary\n\n    Column(\n        horizontalAlignment = Alignment.End,\n        verticalArrangement = Arrangement.spacedBy(1.dp)\n    ) {\n        Row(\n            horizontalArrangement = Arrangement.spacedBy(4.dp),\n            verticalAlignment = Alignment.CenterVertically\n        ) {\n            Text("Net", fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)\n            Text(\n                row?.net?.let(::formatScore) ?: "—",\n                fontSize = 9.sp,\n                fontWeight = FontWeight.SemiBold,\n                color = MaterialTheme.colorScheme.onSurface\n            )\n        }\n        Row(\n            horizontalArrangement = Arrangement.spacedBy(4.dp),\n            verticalAlignment = Alignment.CenterVertically\n        ) {\n            Text(scoreLabel, fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)\n            Text(\n                row?.points?.let(::formatScore) ?: "—",\n                fontSize = 9.sp,\n                fontWeight = FontWeight.Bold,\n                color = accent\n            )\n        }\n    }\n}\n\n@Composable\nprivate fun ExamReportsTab(report: ExamReport, onOpenReports: () -> Unit) {\n'''
if insert_after not in text:
    raise SystemExit('ExamReportsTab insertion point not found')
text = text.replace(insert_after, new_block, 1)
exam.write_text(text, encoding='utf-8')

# Replace the hard-coded purple result accent with the application's theme colors.
dash = Path('app/src/main/java/com/okulyonetim/optikokuyucu/ui/StudentResultDashboard.kt')
d = dash.read_text(encoding='utf-8')
d = d.replace('private val ResultPurple = Color(0xFF5142B5)\n', '')
d = d.replace('fontSize = 27.sp,\n                        fontWeight = FontWeight.Bold,\n                        color = if (result.score != null) ResultPurple else MaterialTheme.colorScheme.onSurfaceVariant',
              'fontSize = 24.sp,\n                        fontWeight = FontWeight.Bold,\n                        color = if (result.score != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant')
d = d.replace('fontSize = 22.sp,\n                        fontWeight = FontWeight.Bold,\n                        color = ResultGreen',
              'fontSize = 20.sp,\n                        fontWeight = FontWeight.Bold,\n                        color = MaterialTheme.colorScheme.secondary')
d = d.replace('TableCell(net, 0.75f, if (!header) ResultPurple else textColor, FontWeight.SemiBold)',
              'TableCell(net, 0.75f, if (!header) MaterialTheme.colorScheme.primary else textColor, FontWeight.SemiBold)')
if 'ResultPurple' in d:
    raise SystemExit('ResultPurple reference remains')
dash.write_text(d, encoding='utf-8')

gradle = Path('app/build.gradle.kts')
g = gradle.read_text(encoding='utf-8')
if 'versionCode = 117' not in g or 'versionName = "0.19.61"' not in g:
    raise SystemExit('Expected version 0.19.61 (117) not found')
g = g.replace('versionCode = 117', 'versionCode = 118', 1)
g = g.replace('versionName = "0.19.61"', 'versionName = "0.19.62"', 1)
gradle.write_text(g, encoding='utf-8')
