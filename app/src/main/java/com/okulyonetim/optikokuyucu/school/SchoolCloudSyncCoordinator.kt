package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import java.security.MessageDigest

/**
 * Cheap local change detector. The UI can ask for synchronization periodically without repeatedly
 * writing Firestore when no exam, paper identity or answer key has changed.
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

    private fun localFingerprint(): String {
        val exams = FileExamRepository(appContext).list()
        val keys = FileAnswerKeyRepository(appContext).list()
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
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val PREFS_NAME = "school-cloud-sync"
        const val KEY_FINGERPRINT = "last-fingerprint"
    }
}
