package com.okulyonetim.optikokuyucu.omr.template

import android.content.Context
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

enum class ActiveTemplateSource {
    STANDARD,
    DESIGNER_DOCUMENT
}

data class ActiveTemplateSelection(
    val source: ActiveTemplateSource,
    val templateId: String,
    val templateVersion: Int
) {
    init {
        require(templateId.isNotBlank())
        require(templateVersion > 0)
    }
}

data class ResolvedActiveTemplate(
    val selection: ActiveTemplateSelection,
    val name: String,
    val template: OmrTemplate,
    val fellBackToDefault: Boolean = false
)

/** Stable default preserving the original production scanner behavior. */
object ActiveOmrTemplateDefaults {
    val template: OmrTemplate = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB
    val selection = ActiveTemplateSelection(
        source = ActiveTemplateSource.STANDARD,
        templateId = template.id,
        templateVersion = template.version
    )
    const val displayName = "20 Soru · ABCD · Öğrenci No · A/B"
}

interface ActiveTemplateSelectionRepository {
    fun load(): ActiveTemplateSelection
    fun save(selection: ActiveTemplateSelection)
    fun reset()
}

/** App-private, atomic selection store. Missing/corrupt state safely falls back to the known default. */
class FileActiveTemplateSelectionRepository(context: Context) : ActiveTemplateSelectionRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    private val file = File(directory, FILE_NAME)

    override fun load(): ActiveTemplateSelection {
        if (!file.isFile) return ActiveOmrTemplateDefaults.selection
        return runCatching { ActiveTemplateSelectionCodec.decode(file.readBytes()) }
            .getOrDefault(ActiveOmrTemplateDefaults.selection)
    }

    override fun save(selection: ActiveTemplateSelection) {
        val temporary = File(directory, "$FILE_NAME.tmp")
        temporary.writeBytes(ActiveTemplateSelectionCodec.encode(selection))
        if (file.exists() && !file.delete()) {
            temporary.delete()
            error("Aktif şablon seçimi güncellenemedi.")
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Aktif şablon seçimi kalıcı depoya taşınamadı.")
        }
    }

    override fun reset() {
        if (file.exists() && !file.delete()) {
            error("Aktif şablon seçimi sıfırlanamadı.")
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-active-template"
        const val FILE_NAME = "selection.omrat"
    }
}

object ActiveTemplateSelectionCodec {
    fun encode(selection: ActiveTemplateSelection): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(SCHEMA_VERSION)
            data.writeUTF(selection.source.name)
            data.writeUTF(selection.templateId)
            data.writeInt(selection.templateVersion)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): ActiveTemplateSelection {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz aktif şablon dosyası." }
            val schema = input.readInt()
            require(schema == SCHEMA_VERSION) { "Desteklenmeyen aktif şablon sürümü: $schema" }
            val selection = ActiveTemplateSelection(
                source = ActiveTemplateSource.valueOf(input.readUTF()),
                templateId = input.readUTF(),
                templateVersion = input.readInt()
            )
            require(input.available() == 0) { "Aktif şablon dosyasında beklenmeyen ek veri var." }
            return selection
        }
    }

    private const val MAGIC = 0x4F4D4154 // OMAT
    private const val SCHEMA_VERSION = 1
}

object ActiveOmrTemplateResolver {
    fun resolve(
        selection: ActiveTemplateSelection,
        savedDocuments: List<DesignerDocument>,
        starterDocuments: List<DesignerDocument> = DesignerStarterTemplates.all()
    ): ResolvedActiveTemplate? = when (selection.source) {
        ActiveTemplateSource.STANDARD -> {
            val standard = ActiveOmrTemplateDefaults.template
            if (selection.templateId == standard.id && selection.templateVersion == standard.version) {
                ResolvedActiveTemplate(
                    selection = selection,
                    name = ActiveOmrTemplateDefaults.displayName,
                    template = standard
                )
            } else {
                null
            }
        }

        ActiveTemplateSource.DESIGNER_DOCUMENT -> {
            val document = savedDocuments.firstOrNull {
                it.id == selection.templateId && it.version == selection.templateVersion
            } ?: starterDocuments.firstOrNull {
                it.id == selection.templateId && it.version == selection.templateVersion
            }
            document?.let {
                ResolvedActiveTemplate(
                    selection = selection,
                    name = it.name,
                    template = DesignerTemplateCompiler.compile(it)
                )
            }
        }
    }

    fun resolveOrDefault(
        selection: ActiveTemplateSelection,
        savedDocuments: List<DesignerDocument>,
        starterDocuments: List<DesignerDocument> = DesignerStarterTemplates.all()
    ): ResolvedActiveTemplate {
        return resolve(selection, savedDocuments, starterDocuments)
            ?: ResolvedActiveTemplate(
                selection = ActiveOmrTemplateDefaults.selection,
                name = ActiveOmrTemplateDefaults.displayName,
                template = ActiveOmrTemplateDefaults.template,
                fellBackToDefault = true
            )
    }
}

/** Convenience entry point used by production UI screens. */
fun resolveActiveOmrTemplate(context: Context): ResolvedActiveTemplate {
    val appContext = context.applicationContext
    val selection = FileActiveTemplateSelectionRepository(appContext).load()
    val savedDocuments = FileDesignerDocumentRepository(appContext).list()
    return ActiveOmrTemplateResolver.resolveOrDefault(selection, savedDocuments)
}
