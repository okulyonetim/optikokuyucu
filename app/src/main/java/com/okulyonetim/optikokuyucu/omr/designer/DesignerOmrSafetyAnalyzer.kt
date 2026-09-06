package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.max
import kotlin.math.min

/**
 * Document-level OMR safety checks that cannot be expressed from the flattened recognition
 * template alone. Mark size/spacing/fiducial checks remain owned by TemplateReadabilityAnalyzer.
 */
object DesignerOmrSafetyAnalyzer {
    fun analyze(document: DesignerDocument): List<ReadabilityIssue> {
        val issues = mutableListOf<ReadabilityIssue>()
        issues += componentPageIssues(document)
        issues += componentOverlapIssues(document)
        issues += questionNumberIssues(document)
        issues += duplicateOmrIdIssues(document)
        return issues
    }

    private fun componentPageIssues(document: DesignerDocument): List<ReadabilityIssue> =
        document.components.mapNotNull { component ->
            val bounds = DesignerComponentGeometry.bounds(component)
            if (bounds.isInside(document.space)) return@mapNotNull null
            ReadabilityIssue(
                severity = ReadabilitySeverity.ERROR,
                type = ReadabilityIssueType.OMR_AREA_OUTSIDE_PAGE,
                elementIds = listOf("component:${component.id}"),
                message = "OMR alanı sayfa sınırlarının dışına taşıyor."
            )
        }

    private fun componentOverlapIssues(document: DesignerDocument): List<ReadabilityIssue> {
        val issues = mutableListOf<ReadabilityIssue>()
        for (firstIndex in document.components.indices) {
            val first = document.components[firstIndex]
            val firstBounds = DesignerComponentGeometry.bounds(first)
            for (secondIndex in firstIndex + 1 until document.components.size) {
                val second = document.components[secondIndex]
                val secondBounds = DesignerComponentGeometry.bounds(second)
                if (!positiveAreaOverlap(firstBounds, secondBounds)) continue
                issues += ReadabilityIssue(
                    severity = ReadabilitySeverity.ERROR,
                    type = ReadabilityIssueType.OMR_AREA_OVERLAP,
                    elementIds = listOf("component:${first.id}", "component:${second.id}"),
                    message = "İki OMR alanı birbiriyle çakışıyor."
                )
            }
        }
        return issues
    }

    /**
     * Question numbers are local to a recognition namespace. Different non-equal prefixes may
     * intentionally both contain questions 1..20 (for example six side-by-side courses).
     */
    private fun questionNumberIssues(document: DesignerDocument): List<ReadabilityIssue> {
        val groups = document.components.filterIsInstance<QuestionGroupComponent>()
        val issues = mutableListOf<ReadabilityIssue>()
        for (firstIndex in groups.indices) {
            val first = groups[firstIndex]
            val firstEnd = first.startQuestion + first.questionCount - 1
            for (secondIndex in firstIndex + 1 until groups.size) {
                val second = groups[secondIndex]
                if (first.questionIdPrefix != second.questionIdPrefix) continue
                val secondEnd = second.startQuestion + second.questionCount - 1
                val overlapStart = max(first.startQuestion, second.startQuestion)
                val overlapEnd = min(firstEnd, secondEnd)
                if (overlapStart > overlapEnd) continue
                val namespace = first.questionIdPrefix.ifBlank { "varsayılan" }
                issues += ReadabilityIssue(
                    severity = ReadabilitySeverity.ERROR,
                    type = ReadabilityIssueType.QUESTION_NUMBER_OVERLAP,
                    elementIds = listOf("component:${first.id}", "component:${second.id}"),
                    message = "Aynı soru kimliği alanında ($namespace) $overlapStart–$overlapEnd soru numaraları çakışıyor."
                )
            }
        }
        return issues
    }

    private fun duplicateOmrIdIssues(document: DesignerDocument): List<ReadabilityIssue> {
        val ownersByReadId = linkedMapOf<String, MutableList<String>>()
        document.components.filterIsInstance<QuestionGroupComponent>().forEach { group ->
            repeat(group.questionCount) { index ->
                val questionNumber = group.startQuestion + index
                val readId = DesignerTemplateCompiler.questionReadId(group, questionNumber)
                ownersByReadId.getOrPut("question:$readId") { mutableListOf() }
                    .add("component:${group.id}")
            }
        }

        // Grid ids are recognition ids as well. DesignerDocument normally guarantees unique
        // component ids, but retaining this check protects imported/legacy documents if that
        // invariant is ever relaxed.
        document.components.filter { it is NumericGridComponent || it is SingleChoiceComponent }
            .forEach { component ->
                ownersByReadId.getOrPut("grid:${component.id}") { mutableListOf() }
                    .add("component:${component.id}")
            }

        return ownersByReadId.mapNotNull { (readId, owners) ->
            if (owners.size < 2) return@mapNotNull null
            ReadabilityIssue(
                severity = ReadabilitySeverity.ERROR,
                type = ReadabilityIssueType.DUPLICATE_OMR_ID,
                elementIds = listOf(readId) + owners.distinct(),
                message = "Aynı OMR okuma kimliği birden fazla alanda kullanılıyor: $readId"
            )
        }
    }

    private fun positiveAreaOverlap(first: TemplateRect, second: TemplateRect): Boolean {
        val width = min(first.right, second.right) - max(first.left, second.left)
        val height = min(first.bottom, second.bottom) - max(first.top, second.top)
        return width > EPSILON && height > EPSILON
    }

    private fun TemplateRect.isInside(space: com.okulyonetim.optikokuyucu.omr.template.TemplateSize): Boolean =
        left >= 0.0 && top >= 0.0 && right <= space.width && bottom <= space.height

    private const val EPSILON = 1e-6
}
