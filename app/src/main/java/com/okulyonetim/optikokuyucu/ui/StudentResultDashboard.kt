package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.StudentLessonResultPresentation
import com.okulyonetim.optikokuyucu.exam.StudentResultPresentation
import java.util.Locale

private val ResultGreen = Color(0xFF3D9B56)
private val ResultRed = Color(0xFFD34848)

@Composable
fun StudentResultHero(
    result: StudentResultPresentation?,
    hasKey: Boolean,
    modifier: Modifier = Modifier
) {
    if (result == null) {
        Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Text(
                if (hasKey) "Sonuç hesaplanıyor…" else "Cevap anahtarı bekleniyor",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResultHeadlineMetric(
                    label = result.scoreLabel,
                    value = result.score?.let(::resultNumber) ?: "—",
                    accent = MaterialTheme.colorScheme.primary,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1.08f)
                )
                ResultHeadlineMetric(
                    label = "Toplam Net",
                    value = result.net?.let(::resultNumber) ?: "—",
                    accent = MaterialTheme.colorScheme.primary,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(0.92f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultMetricPill("Doğru", result.correct?.toString() ?: "—", ResultGreen, Modifier.weight(1f))
                ResultMetricPill("Yanlış", result.wrong?.toString() ?: "—", ResultRed, Modifier.weight(1f))
                ResultMetricPill("Boş", result.blank?.toString() ?: "—", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RankPill("Genel Sıra", result.overallRank?.displayText ?: "—", Modifier.weight(1f))
                RankPill("Sınıf Sırası", result.classRank?.displayText ?: "—", Modifier.weight(1f))
            }

            if (result.scoreNote.isNotBlank()) {
                Text(
                    result.scoreNote,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultHeadlineMetric(
    label: String,
    value: String,
    accent: Color,
    container: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StudentResultLessonDashboard(
    result: StudentResultPresentation,
    modifier: Modifier = Modifier
) {
    if (result.lessons.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LessonResultTable(result.lessons)
        LessonPerformanceChart(result.lessons)
    }
}

@Composable
private fun ResultMetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun RankPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LessonResultTable(lessons: List<StudentLessonResultPresentation>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(
                "Ders Sonuçları",
                modifier = Modifier.padding(horizontal = 12.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            ResultTableRow(
                lesson = "Ders",
                correct = "D",
                wrong = "Y",
                blank = "B",
                net = "Net",
                rank = "Sıra",
                header = true
            )
            lessons.forEach { lesson ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ResultTableRow(
                    lesson = lesson.lessonName,
                    correct = lesson.correct.toString(),
                    wrong = lesson.wrong.toString(),
                    blank = lesson.blank.toString(),
                    net = resultNumber(lesson.net),
                    rank = lesson.rank?.displayText ?: "—",
                    header = false
                )
            }
        }
    }
}

@Composable
private fun ResultTableRow(
    lesson: String,
    correct: String,
    wrong: String,
    blank: String,
    net: String,
    rank: String,
    header: Boolean
) {
    val textColor = if (header) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    val background = if (header) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 10.dp, vertical = if (header) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            lesson,
            modifier = Modifier.weight(1.65f),
            fontSize = if (header) 9.sp else 10.sp,
            fontWeight = if (header) weight else FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TableCell(correct, 0.45f, if (!header) ResultGreen else textColor, weight)
        TableCell(wrong, 0.45f, if (!header) ResultRed else textColor, weight)
        TableCell(blank, 0.45f, textColor, weight)
        TableCell(net, 0.75f, if (!header) MaterialTheme.colorScheme.primary else textColor, FontWeight.SemiBold)
        TableCell(rank, 0.78f, textColor, FontWeight.SemiBold)
    }
}

@Composable
private fun RowScope.TableCell(text: String, weightValue: Float, color: Color, fontWeight: FontWeight) {
    Text(
        text,
        modifier = Modifier.weight(weightValue),
        textAlign = TextAlign.Center,
        fontSize = 9.sp,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1
    )
}

@Composable
private fun LessonPerformanceChart(lessons: List<StudentLessonResultPresentation>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Ders Başarı Grafiği", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Doğru ve yanlış yüzdeleri", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ChartLegend("Doğru", ResultGreen)
                    ChartLegend("Yanlış", ResultRed)
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.width(34.dp).height(116.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    listOf(100, 80, 60, 40, 20, 0).forEach { percent ->
                        Text("%$percent", fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(modifier = Modifier.weight(1f).height(116.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(6) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        lessons.forEach { lesson ->
                            Row(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                ChartBar(lesson.correctPercent, ResultGreen)
                                Spacer(Modifier.width(2.dp))
                                ChartBar(lesson.wrongPercent, ResultRed)
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 34.dp)) {
                lessons.forEach { lesson ->
                    Text(
                        shortLessonName(lesson.lessonName),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartBar(percent: Double, color: Color) {
    Box(
        modifier = Modifier
            .width(9.dp)
            .fillMaxHeight((percent / 100.0).toFloat().coerceIn(0.01f, 1f))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color)
    )
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.width(9.dp).height(9.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun resultNumber(value: Double): String = String.format(Locale.forLanguageTag("tr-TR"), "%.1f", value)

private fun shortLessonName(name: String): String = when {
    name.startsWith("Türkçe", ignoreCase = true) -> "Tür"
    name.startsWith("İnkılap", ignoreCase = true) -> "İnk"
    name.startsWith("Din", ignoreCase = true) -> "Din"
    name.startsWith("Yabancı", ignoreCase = true) -> "Yab"
    name.startsWith("Matematik", ignoreCase = true) -> "Mat"
    name.startsWith("Fen", ignoreCase = true) -> "Fen"
    name.startsWith("Sosyal", ignoreCase = true) -> "Sos"
    else -> name.take(5)
}
