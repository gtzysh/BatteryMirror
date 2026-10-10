package dev.batterymirror.core

/**
 * 【系统版本】从版本号里抽出星云 AIOS 的版本。
 * 这台国行机的显示版本是 NebulaAIOS2.0.13MR6_NX733J，对应星云 AIOS 2.0.13、Android 16。
 */
object SystemBuildParser {
    private val aiosPattern = Regex("(?i)NebulaAIOS([0-9]+(?:\\.[0-9]+)*)")

    fun aiosVersion(vararg texts: String?): String? {
        return texts.firstNotNullOfOrNull { text ->
            if (text.isNullOrBlank()) null else aiosPattern.find(text)?.groupValues?.get(1)
        }
    }

    fun aiosLabel(version: String?): String {
        return if (version.isNullOrBlank()) "星云 AIOS" else "星云 AIOS $version"
    }
}
