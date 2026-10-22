package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.ocr.OcrBookletDraft
import com.okulyonetim.optikokuyucu.ocr.OcrCellReview
import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection
import com.okulyonetim.optikokuyucu.ui.theme.OptikBorder
import com.okulyonetim.optikokuyucu.ui.theme.OptikShape
import com.okulyonetim.optikokuyucu.ui.theme.OptikTextSize
import com.okulyonetim.optikokuyucu.ui.theme.OptikTheme

/** Review state for a scanned answer-key table page (one draft per booklet table). */
@Stable
internal class OcrTableReviewState(initialDrafts: List<OcrBookletDraft>, val warnings: List<String>) {
    var drafts by mutableStateOf(initialDrafts)
    var expanded by mutableStateOf(initialDrafts.indices.toSet())
    var saveIndex by mutableIntStateOf(0)

    fun update(index: Int, draft: OcrBookletDraft) {
        drafts = drafts.mapIndexed { i, old -> if (i == index) draft else old }
    }

    fun toggle(index: Int) {
        expanded = if (index in expanded) expanded - index else expanded + index
    }
}

private const val CELLS_PER_ROW = 5

/**
 * Preview and correction of an answer-key table: every booklet becomes a card, every subject a grid of
 * "number · answer" cells. Missing and doubtful cells are coloured and have to be filled / confirmed before saving.
 */
internal fun LazyListScope.ocrTableReviewItems(
    state: OcrTableReviewState,
    sections: List<ManualAnswerSection>,
    bookletChoices: List<String>,
    onSave: (List<OcrBookletDraft>) -> Unit
) {
    val drafts = state.drafts
    val needsBooklet = bookletChoices.isNotEmpty()

    item(key = "ocr-table-summary") {
        val total = drafts.size * sections.sumOf { it.questionIds.size }
        val answered = drafts.sumOf { it.answeredCount(sections) }
        val pending = drafts.sumOf { it.pendingCount(sections) }
        OcrSectionCard(
            title = "Tablo Okundu",
            subtitle = "${drafts.size} tablo · $answered/$total cevap okundu · $pending hücre kontrol bekliyor"
        ) {
            Text(
                "Renkli hücreler okuyucunun emin olmadığı yerlerdir. Dokunup doğru şıkkı seçin; sarı hücreler onaylanmadan kayıt açılmaz.",
                fontSize = OptikTextSize.micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.warnings.forEach { warning ->
                Text("• $warning", fontSize = OptikTextSize.micro, color = OptikTheme.status.warning.content)
            }
        }
    }

    if (!needsBooklet && drafts.size > 1) {
        item(key = "ocr-table-pick") {
            OcrSectionCard(
                title = "Hangi Tablo Kaydedilsin?",
                subtitle = "Bu sınavın optik formunda kitapçık alanı yok; yalnızca bir tablo cevap anahtarı olur."
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    drafts.forEachIndexed { index, draft ->
                        OcrChoiceButton(
                            Modifier.weight(1f),
                            draft.booklet?.let { "$it Grubu" } ?: "Tablo ${index + 1}",
                            state.saveIndex == index
                        ) { state.saveIndex = index }
                    }
                }
            }
        }
    }

    drafts.forEachIndexed { index, draft ->
        item(key = "ocr-table-$index") {
            OcrBookletDraftCard(
                index = index,
                draft = draft,
                sections = sections,
                bookletChoices = bookletChoices,
                expanded = index in state.expanded,
                onToggle = { state.toggle(index) },
                onChange = { state.update(index, it) }
            )
        }
    }

    item(key = "ocr-table-save") {
        val toSave = if (needsBooklet) drafts else listOfNotNull(drafts.getOrNull(state.saveIndex))
        val pending = toSave.sumOf { it.pendingCount(sections) }
        val bookletProblem = needsBooklet && (
            toSave.any { it.booklet.isNullOrBlank() } || toSave.map { it.booklet }.distinct().size != toSave.size
            )
        OcrSectionCard(
            title = "Cevap Anahtarına Aktar",
            subtitle = when {
                bookletProblem -> "Her tabloya farklı bir kitapçık atayın."
                pending > 0 -> "$pending hücre doldurulmalı veya onaylanmalı."
                else -> "Tüm cevaplar kontrol edildi."
            }
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = toSave.isNotEmpty() && pending == 0 && !bookletProblem,
                onClick = { onSave(toSave) },
                shape = OptikShape.md
            ) {
                Text(if (toSave.size > 1) "${toSave.size} Kitapçığı Kaydet" else "Cevap Anahtarına Kaydet")
            }
        }
    }
}

@Composable
private fun OcrBookletDraftCard(
    index: Int,
    draft: OcrBookletDraft,
    sections: List<ManualAnswerSection>,
    bookletChoices: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (OcrBookletDraft) -> Unit
) {
    val pending = draft.pendingCount(sections)
    var editing by remember { mutableStateOf<Pair<ManualAnswerSection, Int>?>(null) }
    var bookletMenuOpen by remember { mutableStateOf(false) }
    val title = draft.booklet?.let { "Kitapçık $it" } ?: "Tablo ${index + 1}"

    OcrSectionCard(
        title = title,
        subtitle = "${draft.answeredCount(sections)}/${sections.sumOf { it.questionIds.size }} cevap · " +
            if (pending == 0) "kontrol tamam" else "$pending hücre bekliyor"
    ) {
        if (bookletChoices.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { bookletMenuOpen = true },
                    shape = OptikShape.md
                ) {
                    Text("Kitapçık türü: ${draft.booklet ?: "Seç"}", modifier = Modifier.weight(1f))
                    Text("⌄")
                }
                DropdownMenu(expanded = bookletMenuOpen, onDismissRequest = { bookletMenuOpen = false }) {
                    bookletChoices.forEach { value ->
                        DropdownMenuItem(
                            text = { Text("Kitapçık $value") },
                            onClick = {
                                onChange(draft.withBooklet(value))
                                bookletMenuOpen = false
                            }
                        )
                    }
                }
            }
        }
        TextButton(onClick = onToggle) { Text(if (expanded) "Hücreleri gizle ⌃" else "Hücreleri göster ⌄") }

        if (expanded) {
            sections.forEach { section ->
                val subjectPending = (1..section.questionIds.size).count { draft.review(section, it) != OcrCellReview.CLEAN }
                val doubtful = (1..section.questionIds.size).count { draft.review(section, it) == OcrCellReview.NEEDS_CHECK }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${section.label} · ${section.questionIds.size} soru",
                        modifier = Modifier.weight(1f),
                        fontSize = OptikTextSize.body,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (doubtful > 0) {
                        TextButton(onClick = { onChange(draft.withConfirmedSection(section)) }) {
                            Text("$doubtful hücreyi onayla", fontSize = OptikTextSize.micro)
                        }
                    } else if (subjectPending == 0) {
                        Text("✓", color = OptikTheme.status.success.content, fontWeight = FontWeight.Bold)
                    }
                }
                section.questionIds.indices.toList().chunked(CELLS_PER_ROW).forEach { rowIndices ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowIndices.forEach { i ->
                            val number = i + 1
                            OcrAnswerCell(
                                modifier = Modifier.weight(1f),
                                number = number,
                                answer = draft.answers[section.questionIds[i]],
                                review = draft.review(section, number),
                                onClick = { editing = section to number }
                            )
                        }
                        repeat(CELLS_PER_ROW - rowIndices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    editing?.let { (section, number) ->
        val questionId = section.questionIds[number - 1]
        val original = draft.originalCell(section, number)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${section.label} · $number. soru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val read = original?.choice
                    val note = original?.note
                    Text(
                        when {
                            read == null -> "Okuyucu bu hücreyi okuyamadı."
                            note != null -> "Okunan: $read · $note"
                            else -> "Okunan: $read"
                        },
                        fontSize = OptikTextSize.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        section.allowedChoices.sorted().forEach { choice ->
                            val selected = draft.answers[questionId] == choice
                            Surface(
                                modifier = Modifier.size(46.dp).clickable {
                                    onChange(draft.withAnswer(questionId, choice))
                                    editing = null
                                },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(
                                    if (selected) OptikBorder.regular else OptikBorder.hairline,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(choice, fontSize = OptikTextSize.bodyLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Kapat") } },
            dismissButton = {
                TextButton(onClick = {
                    onChange(draft.withoutAnswer(questionId))
                    editing = null
                }) { Text("Boş bırak") }
            }
        )
    }
}

@Composable
private fun OcrAnswerCell(
    modifier: Modifier,
    number: Int,
    answer: String?,
    review: OcrCellReview,
    onClick: () -> Unit
) {
    val status = OptikTheme.status
    val (container, content) = when (review) {
        OcrCellReview.CLEAN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
        OcrCellReview.NEEDS_CHECK -> status.warning.container to status.warning.content
        OcrCellReview.EMPTY -> status.danger.container to status.danger.content
    }
    Surface(
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
        shape = OptikShape.sm,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$number", fontSize = OptikTextSize.micro, textAlign = TextAlign.End)
            Spacer(Modifier.size(5.dp))
            Text(answer ?: "–", fontSize = OptikTextSize.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}
