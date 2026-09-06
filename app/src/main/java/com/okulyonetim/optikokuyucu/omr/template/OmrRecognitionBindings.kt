package com.okulyonetim.optikokuyucu.omr.template

import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord

/**
 * Semantic bindings for generic mark-grid ids used by the production OMR flow.
 *
 * This is transient metadata only: recognition geometry still lives exclusively in [OmrTemplate].
 * Designer-generated forms keep their compiled component ids (number-1, booklet-1, ...), while
 * legacy production templates keep the original studentNumber/class/booklet ids.
 */
data class OmrRecognitionBindings(
    val studentNumberGridId: String? = null,
    val classGridId: String? = null,
    val bookletGridId: String? = null
) {
    init {
        require(studentNumberGridId?.isNotBlank() != false)
        require(classGridId?.isNotBlank() != false)
        require(bookletGridId?.isNotBlank() != false)
    }

    fun studentNumber(result: MarkGridReadResult): String? = value(result, studentNumberGridId)
    fun classCode(result: MarkGridReadResult): String? = value(result, classGridId)
    fun booklet(result: MarkGridReadResult): String? = value(result, bookletGridId)

    fun studentNumber(record: ScanRecord): String? = value(record, studentNumberGridId)
    fun classCode(record: ScanRecord): String? = value(record, classGridId)
    fun booklet(record: ScanRecord): String? = value(record, bookletGridId)

    private fun value(result: MarkGridReadResult, gridId: String?): String? =
        gridId?.let { result.grid(it)?.value }

    private fun value(record: ScanRecord, gridId: String?): String? =
        gridId?.let { record.grid(it)?.value }
}

/** Resolves semantics from the exact compiled template/record without defining a second template. */
object OmrRecognitionBindingsResolver {
    fun fromTemplate(template: OmrTemplate): OmrRecognitionBindings =
        fromGridIds(template.markGrids.map { it.id })

    fun fromRecord(record: ScanRecord): OmrRecognitionBindings =
        fromGridIds(record.markGrids.map { it.gridId })

    fun fromGridIds(gridIds: List<String>): OmrRecognitionBindings {
        val ids = gridIds.distinct()
        return OmrRecognitionBindings(
            studentNumberGridId = ids.firstOrNull { it == LEGACY_STUDENT_NUMBER } ?: ids.firstOrNull { it.startsWith(DESIGNER_NUMBER_PREFIX) },
            classGridId = ids.firstOrNull { it == LEGACY_CLASS },
            bookletGridId = ids.firstOrNull { it == LEGACY_BOOKLET } ?: ids.firstOrNull { it.startsWith(DESIGNER_BOOKLET_PREFIX) }
        )
    }

    private const val LEGACY_STUDENT_NUMBER = "studentNumber"
    private const val LEGACY_CLASS = "class"
    private const val LEGACY_BOOKLET = "booklet"
    private const val DESIGNER_NUMBER_PREFIX = "number-"
    private const val DESIGNER_BOOKLET_PREFIX = "booklet-"
}
