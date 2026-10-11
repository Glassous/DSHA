package com.glassous.dsha.data

/**
 * 语义化版本号的解析与比较，只服务于更新检测：GitHub Release 的 tag 与应用自身的 versionName 都用它比大小。
 *
 * 比较遵循语义化版本规范，但对段数差异很宽松——`1.0` 与 `1.0.0` 视为同一版本，
 * 免得应用版本号写法与 tag 写法不完全一致时误报更新。任一侧解析失败一律按"不比它新"处理，
 * 宁可漏报一次，也不要在无法判断时弹一个更新提示出来。
 */
object SemanticVersion {

    /**
     * 可选的 `v` 前缀、至少一段数字、可选的 `-预发布` 与 `+构建元数据`。
     * 用 [Regex.matchEntire] 要求整串匹配，`v1.0.0 修复` 这类带说明的文字不会被当成版本号。
     */
    private val PATTERN = Regex(
        "v?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.\\-]+))?(?:\\+[0-9A-Za-z.\\-]+)?",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 归一化为可展示、可比较的版本号：去掉 `v` 前缀与构建元数据，保留预发布后缀
     * （`v1.0.1-rc.2+build5` → `1.0.1-rc.2`）。无法解析时返回 null。
     */
    fun normalize(raw: String?): String? {
        val parsed = parse(raw) ?: return null
        val core = parsed.numbers.joinToString(".")
        return if (parsed.preRelease.isEmpty()) core else "$core-${parsed.preRelease.joinToString(".")}"
    }

    /** 候选版本是否比当前版本更新；任一侧解析失败时返回 false。 */
    fun isNewer(candidate: String?, current: String?): Boolean {
        val newer = parse(candidate) ?: return false
        val older = parse(current) ?: return false
        return compare(newer, older) > 0
    }

    private class Version(val numbers: List<Int>, val preRelease: List<String>)

    private fun parse(raw: String?): Version? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val match = PATTERN.matchEntire(text) ?: return null
        val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
        val preRelease = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
        return Version(numbers, preRelease)
    }

    private fun compare(newer: Version, older: Version): Int {
        // 逐段比较数字，缺的段补 0：1.0 与 1.0.0 因此相等
        for (index in 0 until maxOf(newer.numbers.size, older.numbers.size)) {
            val diff = newer.numbers.getOrElse(index) { 0 } - older.numbers.getOrElse(index) { 0 }
            if (diff != 0) return diff
        }
        return comparePreRelease(newer.preRelease, older.preRelease)
    }

    /** 数字段相同时：正式版大于预发布版（1.0.0 > 1.0.0-rc.1），都是预发布版则逐段比较标识符。 */
    private fun comparePreRelease(newer: List<String>, older: List<String>): Int {
        return when {
            newer.isEmpty() && older.isEmpty() -> 0
            newer.isEmpty() -> 1
            older.isEmpty() -> -1
            else -> {
                for (index in 0 until minOf(newer.size, older.size)) {
                    val diff = compareIdentifier(newer[index], older[index])
                    if (diff != 0) return diff
                }
                newer.size - older.size
            }
        }
    }

    /** 单段标识符：两边都是数字按数值比；数字段优先级低于字母段；其余按字典序。 */
    private fun compareIdentifier(newer: String, older: String): Int {
        val newerNumber = newer.toIntOrNull()
        val olderNumber = older.toIntOrNull()
        return when {
            newerNumber != null && olderNumber != null -> newerNumber - olderNumber
            newerNumber != null -> -1
            olderNumber != null -> 1
            else -> newer.compareTo(older)
        }
    }
}
