package com.novelverse.core.model

enum class ReadingStatus(val label: String) {
    PLAN_TO_READ("Plan to read"), READING("Reading"), COMPLETED("Completed"),
    ON_HOLD("On hold"), DROPPED("Dropped")
}

data class Novel(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val readingStatus: ReadingStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val inLibrary: Boolean = true,
)

enum class AppTheme(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), OLED("OLED") }
enum class ReaderMode(val label: String) { CONTINUOUS("Continuous"), PAGINATED("Paginated") }
enum class LibraryLayout(val label: String) { GRID("Grid"), LIST("List") }

data class UserPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val readerMode: ReaderMode = ReaderMode.CONTINUOUS,
    val fontSizeSp: Int = 20,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
)
