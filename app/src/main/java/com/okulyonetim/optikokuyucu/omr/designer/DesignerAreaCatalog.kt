package com.okulyonetim.optikokuyucu.omr.designer

enum class DesignerAreaKind(val displayName: String) {
    NUMBER("Numara"),
    ANSWERS("Cevaplar"),
    DESCRIPTION("Açıklama"),
    IMAGE("Resim")
}

data class DesignerAreaSection(
    val title: String,
    val kinds: List<DesignerAreaKind>
) {
    init {
        require(title.isNotBlank())
        require(kinds.isNotEmpty())
        require(kinds.distinct().size == kinds.size)
    }
}

/**
 * Single catalog used by the unified editor when the user taps + to add a field.
 * Actual field geometry is created by the dedicated editor stages; this catalog only owns which
 * field families are offered and prevents UI screens from maintaining parallel option lists.
 */
object DesignerAreaCatalog {
    val sections: List<DesignerAreaSection> = listOf(
        DesignerAreaSection(
            title = "İşaretleme Alanı",
            kinds = listOf(
                DesignerAreaKind.NUMBER,
                DesignerAreaKind.ANSWERS
            )
        ),
        DesignerAreaSection(
            title = "Bilgilendirme Alanı",
            kinds = listOf(
                DesignerAreaKind.DESCRIPTION,
                DesignerAreaKind.IMAGE
            )
        )
    )

    val allKinds: List<DesignerAreaKind> = sections.flatMap { it.kinds }

    init {
        require(allKinds.distinct().size == allKinds.size) {
            "An optical form area kind may appear only once in the add-area catalog."
        }
    }
}
