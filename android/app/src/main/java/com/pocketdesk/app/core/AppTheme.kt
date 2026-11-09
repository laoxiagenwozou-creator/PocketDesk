package com.pocketdesk.app.core

/**
 * Appearance choices the user can switch between, kept in core so both the
 * service (persistence) and the UI (colour schemes) can use one list.
 *
 * Night is the default; the three day skins are the palettes chosen from the
 * design round: warm sand, cool slate, frost moss.
 */
enum class AppTheme(
    val id: String,
    val label: String,
    val isLight: Boolean,
    /** System bar / window background for this theme. */
    val barColor: Long,
) {
    NIGHT("night", "夜间", false, 0xFF14161A),
    SAND("sand", "暖砂纸", true, 0xFFF4F1EA),
    SLATE("slate", "冷灰蓝", true, 0xFFF1F3F5),
    MOSS("moss", "霜白苔青", true, 0xFFF3F4EF),
    HAPTIC("haptic", "静瓷", true, 0xFFE7EBF2);

    companion object {
        fun byId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: NIGHT
    }
}
