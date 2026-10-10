package dev.batterymirror.core

/**
 * 【记录】用电样本和容量日志写成一行一个对象，避免再引序列化库。
 * 只存数字和短代码，不存账号、序列号或 IMEI。
 */
object SampleCodec {
    fun encode(sample: UsageSample): String {
        val parts = mutableListOf(
            "\"t\":${sample.epochMillis}",
            "\"lv\":${sample.levelPercent}",
            "\"st\":\"${statusCode(sample.status)}\"",
            "\"sc\":${if (sample.screenInteractive) 1 else 0}",
        )
        sample.currentMicroamps?.let { parts += "\"ua\":$it" }
        sample.voltageMv?.let { parts += "\"mv\":$it" }
        sample.temperatureTenthsC?.let { parts += "\"tt\":$it" }
        sample.remainingUah?.let { parts += "\"rm\":$it" }
        return "{" + parts.joinToString(",") + "}"
    }

    fun decode(line: String): UsageSample? {
        val map = parseFlat(line) ?: return null
        val time = map.long("t") ?: return null
        val level = map.int("lv") ?: return null
        return UsageSample(
            epochMillis = time,
            levelPercent = level.coerceIn(0, 100),
            currentMicroamps = map.int("ua"),
            voltageMv = map.int("mv"),
            temperatureTenthsC = map.int("tt"),
            remainingUah = map.long("rm"),
            status = statusFrom(map.str("st")),
            screenInteractive = map.int("sc") == 1,
        )
    }

    fun encodeCapacity(point: CapacityPoint): String {
        return "{\"t\":${point.epochMillis},\"mah\":${point.fullMah},\"src\":\"${point.sourceCode}\"}"
    }

    fun decodeCapacity(line: String): CapacityPoint? {
        val map = parseFlat(line) ?: return null
        val time = map.long("t") ?: return null
        val mah = map.double("mah") ?: return null
        val source = map.str("src") ?: return null
        if (mah <= 0.0) return null
        return CapacityPoint(time, mah, source)
    }

    private fun statusCode(status: ChargeStatus): String = when (status) {
        ChargeStatus.UNKNOWN -> "U"
        ChargeStatus.CHARGING -> "C"
        ChargeStatus.DISCHARGING -> "D"
        ChargeStatus.FULL -> "F"
        ChargeStatus.NOT_CHARGING -> "N"
    }

    private fun statusFrom(code: String?): ChargeStatus = when (code) {
        "C" -> ChargeStatus.CHARGING
        "D" -> ChargeStatus.DISCHARGING
        "F" -> ChargeStatus.FULL
        "N" -> ChargeStatus.NOT_CHARGING
        else -> ChargeStatus.UNKNOWN
    }

    private class Flat(val raw: Map<String, String>) {
        fun long(key: String): Long? = raw[key]?.toLongOrNull()
        fun int(key: String): Int? = raw[key]?.toIntOrNull() ?: raw[key]?.toDoubleOrNull()?.toInt()
        fun double(key: String): Double? = raw[key]?.toDoubleOrNull()
        fun str(key: String): String? = raw[key]
    }

    private fun parseFlat(json: String): Flat? {
        val body = json.trim()
        if (!body.startsWith("{") || !body.endsWith("}")) return null
        val inner = body.substring(1, body.length - 1).trim()
        if (inner.isEmpty()) return Flat(emptyMap())
        val map = linkedMapOf<String, String>()
        var index = 0
        fun skip() {
            while (index < inner.length && inner[index].isWhitespace()) index++
        }
        while (index < inner.length) {
            skip()
            if (index >= inner.length || inner[index] != '"') return null
            index++
            val keyStart = index
            while (index < inner.length && inner[index] != '"') index++
            if (index >= inner.length) return null
            val key = inner.substring(keyStart, index)
            index++
            skip()
            if (index >= inner.length || inner[index] != ':') return null
            index++
            skip()
            if (index >= inner.length) return null
            val value = if (inner[index] == '"') {
                index++
                val valueStart = index
                while (index < inner.length && inner[index] != '"') index++
                if (index >= inner.length) return null
                val text = inner.substring(valueStart, index)
                index++
                text
            } else {
                val valueStart = index
                while (index < inner.length && inner[index] != ',') index++
                inner.substring(valueStart, index).trim()
            }
            map[key] = value
            skip()
            if (index < inner.length && inner[index] == ',') index++
        }
        return Flat(map)
    }
}
