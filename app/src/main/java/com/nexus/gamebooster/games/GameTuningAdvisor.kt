package com.nexus.gamebooster.games

/**
 * Conservative, game-specific advice for supported titles. This class never changes game
 * settings or claims to read FPS; it only identifies a title from the library's displayed
 * name/package and returns actionable settings for the player to apply in-game.
 */
object GameTuningAdvisor {
    enum class Title { PUBG_MOBILE, CALL_OF_DUTY_MOBILE }

    fun identify(nameOrPackage: String?): Title? {
        val value = nameOrPackage?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return null
        return when {
            value.contains("pubg") || value.contains("bgmi") || value.contains("com.tencent.ig") ||
                value.contains("com.pubg.krmobile") || value.contains("com.pubg.imobile") ||
                value.contains("com.vng.pubgmobile") || value.contains("com.rekoo.pubgm") -> Title.PUBG_MOBILE
            value.contains("call of duty") || value.contains("cod mobile") || value.contains("codm") ||
                value.contains("com.activision.callofduty.shooter") || value.contains("com.garena.game.codm") ||
                value.contains("com.tencent.tmgp.cod") -> Title.CALL_OF_DUTY_MOBILE
            else -> null
        }
    }
}
