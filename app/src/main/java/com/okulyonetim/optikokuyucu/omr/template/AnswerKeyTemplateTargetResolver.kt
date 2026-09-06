package com.okulyonetim.optikokuyucu.omr.template

import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates

/**
 * Exact template resolver used by answer-key capture.
 *
 * Answer keys must never be captured against a silent fallback when a selected historical
 * designer version is unavailable. The selection repository already supplies the known standard
 * default when no valid selection has ever been stored; once a concrete selection exists, its
 * id/version is resolved exactly here.
 */
object AnswerKeyTemplateTargetResolver {
    fun resolve(
        selection: ActiveTemplateSelection,
        savedDocuments: List<DesignerDocument>,
        starterDocuments: List<DesignerDocument> = DesignerStarterTemplates.all()
    ): ResolvedActiveTemplate? = ActiveOmrTemplateResolver.resolve(
        selection = selection,
        savedDocuments = savedDocuments,
        starterDocuments = starterDocuments
    )
}
