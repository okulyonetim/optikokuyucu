package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import java.security.MessageDigest

/** Pure fingerprint used by the dirty detector and unit tests. */
object SchoolCloudFingerprint {
    fun digest(
        exams: List<Exam>,
        keys: List<StoredAnswerKey>,
        records: List<ScanRecord>
    ): String {
        val identity = buildString {
            exams.sortedBy { it.id }.forEach { exam ->
                append(exam.id).append('|')
                append(exam.name).append('|')
                append(exam.examDateEpochDay).append('|')
                append(exam.wrongAnswerPolicy.name).append('|')
                append(exam.templateSelection.templateId).append(':')
                append(exam.templateSelection.templateVersion).append('|')
                exam.participants.sortedBy { it.studentNumber }.forEach { participant ->
                    append('P').append(participant.studentNumber).append(':')
                    append(participant.studentName).append(':').append(participant.className).append('|')
                }
                exam.papers.sortedBy { it.scanRecordId }.forEach { paper ->
                    append('R').append(paper.scanRecordId).append(':')
                    append(paper.studentNumber).append(':').append(paper.studentName).append(':')
                    append(paper.className).append(':').append(paper.bookletCode).append('|')
                }
            }
            keys.sortedWith(compareBy({ it.templateId }, { it.templateVersion }, { it.variantValue ?: "" }))
                .forEach { key ->
                    append('K').append(key.templateId).append(':').append(key.templateVersion).append(':')
                    append(key.variantValue.orEmpty()).append(':').append(key.createdAtEpochMs).append(':')
                    key.answerKey.answers.toSortedMap().forEach { (question, answer) ->
                        append(question).append('=').append(answer).append(',')
                    }
                    append('|')
                }
            records.sortedBy { it.id }.forEach { record ->
                append('S').append(record.id).append(':')
                append(record.templateId).append(':').append(record.templateVersion).append(':')
                append(record.capturedAtEpochMs).append(':')
                record.answers.sortedBy { it.questionId }.forEach { answer ->
                    append(answer.questionId).append('=')
                    append(answer.state.name).append(':')
                    append(answer.selectedChoice.orEmpty()).append(',')
                }
                append(':')
                record.markGrids.sortedBy { it.gridId }.forEach { grid ->
                    append(grid.gridId).append('[')
                    grid.columns.sortedBy { it.columnId }.forEach { column ->
                        append(column.columnId).append('=')
                        append(column.state.name).append(':')
                        append(column.selectedValue.orEmpty()).append(',')
                    }
                    append(']')
                }
                append('|')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }
}

/**
 * Cheap local change detector. The UI can ask for synchronization periodically without repeatedly
 * writing Firestore when no exam, paper identity, raw scan result or answer key has changed.
 */
class SchoolCloudSyncCoordinator(
    context: Context,
    private val client: SchoolPortalClient
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun syncIfChanged(force: Boolean = false): SchoolExamCloudSyncResult? {
        val fingerprint = localFingerprint()
        if (!force && fingerprint == prefs.getString(KEY_FINGERPRINT, "")) return null
        val result = SchoolExamCloudSyncService(appContext, client).syncAll()
        if (result.failures.isEmpty()) {
            prefs.edit().putString(KEY_FINGERPRINT, fingerprint).apply()
        }
        return result
    }

    fun invalidate() {
        prefs.edit().remove(KEY_FINGERPRINT).apply()
    }

    private fun localFingerprint(): String = SchoolCloudFingerprint.digest(
        exams = FileExamRepository(appContext).list(),
        keys = FileAnswerKeyRepository(appContext).list(),
        records = FileScanRecordRepository(appContext).list()
    )

    private companion object {
        const val PREFS_NAME = "school-cloud-sync"
        const val KEY_FINGERPRINT = "last-fingerprint"
    }
}
