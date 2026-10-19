package com.emberfall.game.entity

/**
 * Presentation data for each boss. Game.kt shows introLine when a fight
 * starts and defeatLine above the boss when it dies.
 */
data class BossSignature(
    val id: String,
    val introLine: String,
    val defeatLine: String,
    val telegraphColor: Int,
    val signatureMove: String,
    val tell: String,
)

object BossSignatures {

    val ALL: List<BossSignature> = listOf(
        BossSignature(
            id = "gloomfang",
            introLine = "The ground rolls itself into a shell and starts to hum.",
            defeatLine = "The armour cracks open, and the hum fades into the dirt.",
            telegraphColor = 0x66B0A8C8,
            signatureMove = "Armoured roll: a long charge that takes a hit to stop.",
            tell = "The back plates lift and rattle before each roll.",
        ),
        BossSignature(
            id = "stonewarden",
            introLine = "A shadow crosses the sand, and the air goes cold.",
            defeatLine = "The bird folds its wings and drops like a stone.",
            telegraphColor = 0x66C8D0E0,
            signatureMove = "Dive from above: a fast swoop with a narrow strike line.",
            tell = "Its shadow grows on the ground before it dives.",
        ),
        BossSignature(
            id = "mirewitch",
            introLine = "The mud bubbles, and something under it starts digging.",
            defeatLine = "The fen goes quiet, and the mud stops moving.",
            telegraphColor = 0x6685B88E,
            signatureMove = "Burrows underground to recover, then surfaces somewhere else.",
            tell = "Dirt spits up in a ring just before it surfaces.",
        ),
        BossSignature(
            id = "ashtitan",
            introLine = "Embers spark in the grass, and a low growl rolls over the hills.",
            defeatLine = "The tiger's last flames gutter out in the ash.",
            telegraphColor = 0x66FF8A4A,
            signatureMove = "Longer pounce that leaves a scorched trail.",
            tell = "It crouches low, and its flanks glow before it leaps.",
        ),
        BossSignature(
            id = "hollowking",
            introLine = "The wolves stop howling. Then one long note rises from the ruins.",
            defeatLine = "The King kneels, and the ruins are silent for the first time.",
            telegraphColor = 0x668E7BB8,
            signatureMove = "Ambush rush: it circles, then lunges from an unexpected angle.",
            tell = "Its eyes flare, and it circles once before the rush.",
        ),
    )

    fun byId(id: String): BossSignature? = ALL.firstOrNull { it.id == id }
}
