from pathlib import Path

exam_path = Path("app/src/main/java/com/okulyonetim/optikokuyucu/ui/ExamDetailScreen.kt")
text = exam_path.read_text(encoding="utf-8")

# Keep the student identity row as wide as possible: the score is already shown
# in the metrics row, so the duplicate badge beside the name is removed.
score_badge = '''                ProductStatusBadge(
                    text = if (row.points == null) {
                        if (lgs) "LGS —" else "PUAN —"
                    } else {
                        if (lgs) "LGS ${formatScore(row.points)}" else formatScore(row.points)
                    },
                    tone = if (row.points == null) ProductBadgeTone.ORANGE else ProductBadgeTone.GREEN
                )
'''
if score_badge not in text:
    raise SystemExit("Expected ranked-result score badge block not found")
text = text.replace(score_badge, "", 1)

old_metrics = '''                ExamResultMetric("Toplam D", row.correct?.toString() ?: "—", Modifier.weight(1f))
                ExamResultMetric("Toplam Y", row.wrong?.toString() ?: "—", Modifier.weight(1f))
                ExamResultMetric("Toplam Net", row.net?.let(::formatScore) ?: "—", Modifier.weight(1f))
                ExamResultMetric(scoreTitle, row.points?.let(::formatScore) ?: "—", Modifier.weight(1f), emphasize = true)
'''
new_metrics = '''                ExamResultMetric("Doğru", row.correct?.toString() ?: "—", Modifier.weight(1f))
                ExamResultMetric("Yanlış", row.wrong?.toString() ?: "—", Modifier.weight(1f))
                ExamResultMetric("Net", row.net?.let(::formatScore) ?: "—", Modifier.weight(0.88f))
                ExamResultMetric(
                    if (lgs) "LGS Puanı" else scoreTitle,
                    row.points?.let(::formatScore) ?: "—",
                    Modifier.weight(0.92f),
                    emphasize = true
                )
'''
if old_metrics not in text:
    raise SystemExit("Expected ranked-result metrics block not found")
text = text.replace(old_metrics, new_metrics, 1)

old_metric_style = '''        Text(
            label,
            fontSize = 7.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
'''
new_metric_style = '''        Text(
            label,
            fontSize = 6.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            fontSize = if (emphasize) 9.5.sp else 9.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
'''
if old_metric_style not in text:
    raise SystemExit("Expected ExamResultMetric style block not found")
text = text.replace(old_metric_style, new_metric_style, 1)

exam_path.write_text(text, encoding="utf-8")

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text(encoding="utf-8")
if 'versionName = "0.19.61"' not in gradle:
    if 'versionCode = 116' not in gradle or 'versionName = "0.19.60"' not in gradle:
        raise SystemExit("Expected app version 0.19.60 (116) not found")
    gradle = gradle.replace("versionCode = 116", "versionCode = 117", 1)
    gradle = gradle.replace('versionName = "0.19.60"', 'versionName = "0.19.61"', 1)
    gradle_path.write_text(gradle, encoding="utf-8")
