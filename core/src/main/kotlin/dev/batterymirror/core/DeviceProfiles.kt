package dev.batterymirror.core

/**
 * 【机型】努比亚 Z70 系列官网典型容量。
 * 国行 Z70 Ultra 常见代号 NX733J、卫星版 NX736J，典型容量 6150mAh。
 * Z70S Ultra 常见代号 NX737J，典型容量 6600mAh。
 * 部分 Z70S 国际版也印过 NX733J，所以没有星云 AIOS 字样时不直接下结论。
 */
object DeviceProfiles {
    val z70Ultra = DeviceProfile(
        id = "z70_ultra",
        name = "努比亚 Z70 Ultra",
        typicalMah = 6150,
        wiredChargeWatts = 80,
        ambiguous = false,
        note = "官网典型容量 6150mAh，80W 有线快充。国行型号 NX733J、NX736J。",
    )

    val z70sUltra = DeviceProfile(
        id = "z70s_ultra",
        name = "努比亚 Z70S Ultra",
        typicalMah = 6600,
        wiredChargeWatts = 80,
        ambiguous = false,
        note = "官网典型容量 6600mAh，80W 有线快充。常见型号 NX737J。",
    )

    val z70Ambiguous = DeviceProfile(
        id = "z70_ambiguous",
        name = "努比亚 Z70 系列",
        typicalMah = 6150,
        wiredChargeWatts = 80,
        ambiguous = true,
        note = "只看到 NX733J，分不清是 6150mAh 的 Z70 Ultra，还是 6600mAh 的 Z70S 国际版。请在设置里确认。",
    )

    fun match(identity: String): DeviceProfile? {
        val compact = identity.lowercase().replace(Regex("[\\s_\\-]+"), "")
        val mentionsS = "z70s" in compact || "nx737" in compact
        val nx733 = "nx733" in compact
        val nx736 = "nx736" in compact
        val mentionsZ70 = "z70" in compact || nx733 || nx736 || "nx737" in compact
        if (!mentionsZ70) return null
        if (mentionsS) return z70sUltra
        if (nx736 || "z70ultra" in compact) return z70Ultra
        // 【国行系统】星云 AIOS 上的 NX733J 是 Z70 Ultra，不是 Z70S。
        val domesticAios = "nebulaaios" in compact || "星云" in identity
        if (nx733 && domesticAios) return z70Ultra
        if ("z70" in compact && "ultra" in compact) return z70Ultra
        return z70Ambiguous
    }

    fun resolve(choice: ProfileChoice, identity: String): DeviceProfile? = when (choice) {
        ProfileChoice.Z70_ULTRA -> z70Ultra
        ProfileChoice.Z70S_ULTRA -> z70sUltra
        ProfileChoice.GENERIC -> null
        ProfileChoice.AUTO -> match(identity)
    }
}
