package com.okulyonetim.optikokuyucu.omr.designer

/** Initial starter pack for the future form-designer template gallery. */
object DesignerStarterTemplates {
    fun all(): List<DesignerDocument> = listOf(
        questions20Abcd(),
        questions40Abcd(),
        questions50Abcde(),
        questions80Abcd(),
        questions100Abcd(),
        DesignerPhysicalTestPack.document()
    )

    fun questions20Abcd(): DesignerDocument = DesignerDocument(
        id = "starter-20-abcd",
        version = 1,
        name = "20 Soru · ABCD",
        components = listOf(
            QuestionGroupComponent(
                id = "questions",
                startQuestion = 1,
                questionCount = 20,
                choices = listOf("A", "B", "C", "D"),
                columns = 1,
                firstChoiceX = 430.0,
                topY = 300.0,
                bubbleRadius = 14.0,
                choiceGap = 95.0,
                rowGap = 48.0,
                columnGap = 480.0
            )
        )
    )

    fun questions40Abcd(): DesignerDocument = DesignerDocument(
        id = "starter-40-abcd",
        version = 1,
        name = "40 Soru · ABCD",
        components = listOf(
            QuestionGroupComponent(
                id = "questions",
                startQuestion = 1,
                questionCount = 40,
                choices = listOf("A", "B", "C", "D"),
                columns = 2,
                firstChoiceX = 150.0,
                topY = 270.0,
                bubbleRadius = 11.0,
                choiceGap = 52.0,
                rowGap = 48.0,
                columnGap = 500.0
            )
        )
    )

    fun questions50Abcde(): DesignerDocument = DesignerDocument(
        id = "starter-50-abcde",
        version = 1,
        name = "50 Soru · ABCDE",
        components = listOf(
            QuestionGroupComponent(
                id = "questions",
                startQuestion = 1,
                questionCount = 50,
                choices = listOf("A", "B", "C", "D", "E"),
                columns = 2,
                firstChoiceX = 110.0,
                topY = 175.0,
                bubbleRadius = 10.5,
                choiceGap = 45.0,
                rowGap = 43.0,
                columnGap = 500.0
            )
        )
    )

    fun questions80Abcd(): DesignerDocument = DesignerDocument(
        id = "starter-80-abcd",
        version = 1,
        name = "80 Soru · ABCD",
        components = listOf(
            QuestionGroupComponent(
                id = "questions",
                startQuestion = 1,
                questionCount = 80,
                choices = listOf("A", "B", "C", "D"),
                columns = 4,
                firstChoiceX = 90.0,
                topY = 220.0,
                bubbleRadius = 10.5,
                choiceGap = 38.0,
                rowGap = 48.0,
                columnGap = 235.0
            )
        )
    )

    fun questions100Abcd(): DesignerDocument = DesignerDocument(
        id = "starter-100-abcd",
        version = 1,
        name = "100 Soru · ABCD",
        components = listOf(
            QuestionGroupComponent(
                id = "questions",
                startQuestion = 1,
                questionCount = 100,
                choices = listOf("A", "B", "C", "D"),
                columns = 4,
                firstChoiceX = 110.0,
                topY = 175.0,
                bubbleRadius = 10.5,
                choiceGap = 38.0,
                rowGap = 43.0,
                columnGap = 240.0
            )
        )
    )
}
