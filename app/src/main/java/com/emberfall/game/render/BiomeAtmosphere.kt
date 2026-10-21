package com.emberfall.game.world

enum class AmbientParticle { NONE, EMBERS, POLLEN, SEA_SPRAY, LEAVES, DUST }

/**
 * Per-biome atmosphere. WorldRenderer reads it to tint the air and spawn drift.
 */
data class AtmosphereProfile(
    val fogColor: Int,
    val fogStrength: Float,
    val windStrength: Float,
    val particle: AmbientParticle,
    val particleRatePerSecond: Float,
    val soundBed: String,
    val flavor: String,
)

object BiomeAtmosphere {

    fun of(biome: Biome): AtmosphereProfile = when (biome) {
        Biome.OCEAN -> AtmosphereProfile(
            fogColor = 0xFF6F9CB8.toInt(), fogStrength = 0.10f, windStrength = 0.7f,
            particle = AmbientParticle.SEA_SPRAY, particleRatePerSecond = 1.5f,
            soundBed = "gulls_and_swell",
            flavor = "Salt wind and a long grey horizon.",
        )
        Biome.SHORE -> AtmosphereProfile(
            fogColor = 0xFFAFC7C9.toInt(), fogStrength = 0.14f, windStrength = 0.6f,
            particle = AmbientParticle.SEA_SPRAY, particleRatePerSecond = 2.5f,
            soundBed = "surf_wash",
            flavor = "Foam hisses over the sand.",
        )
        Biome.PLAINS -> AtmosphereProfile(
            fogColor = 0xFFD9C98F.toInt(), fogStrength = 0.04f, windStrength = 0.5f,
            particle = AmbientParticle.DUST, particleRatePerSecond = 0.8f,
            soundBed = "open_wind",
            flavor = "Grass bows in long waves.",
        )
        Biome.FOREST -> AtmosphereProfile(
            fogColor = 0xFF3E5A3A.toInt(), fogStrength = 0.22f, windStrength = 0.25f,
            particle = AmbientParticle.POLLEN, particleRatePerSecond = 1.2f,
            soundBed = "forest_canopy",
            flavor = "Light comes down in green shafts.",
        )
        Biome.HIGHLANDS -> AtmosphereProfile(
            fogColor = 0xFFB8BFD0.toInt(), fogStrength = 0.18f, windStrength = 0.9f,
            particle = AmbientParticle.LEAVES, particleRatePerSecond = 0.4f,
            soundBed = "high_wind",
            flavor = "Thin air and a cold, hard wind.",
        )
        Biome.RUINS -> AtmosphereProfile(
            fogColor = 0xFF8A3A1E.toInt(), fogStrength = 0.26f, windStrength = 0.15f,
            particle = AmbientParticle.EMBERS, particleRatePerSecond = 2.0f,
            soundBed = "ruin_drone",
            flavor = "Old stone hums, and embers drift up from nowhere.",
        )
        Biome.SNOW -> AtmosphereProfile(
            fogColor = 0xFFC4E1F0.toInt(), fogStrength = .18f, windStrength = .85f,
            particle = AmbientParticle.DUST, particleRatePerSecond = 1f,
            soundBed = "snow_wind", flavor = "The cold wind carries snow across the peaks.",
        )
        Biome.VOLCANIC -> AtmosphereProfile(
            fogColor = 0xFF9C4931.toInt(), fogStrength = .24f, windStrength = .35f,
            particle = AmbientParticle.EMBERS, particleRatePerSecond = 2.5f,
            soundBed = "volcano", flavor = "Ash and embers rise from the broken earth.",
        )
        Biome.DESERT -> AtmosphereProfile(
            fogColor = 0xFFE0B779.toInt(), fogStrength = .12f, windStrength = .7f,
            particle = AmbientParticle.DUST, particleRatePerSecond = 1.8f,
            soundBed = "desert_wind", flavor = "Warm sand drifts over old tracks.",
        )
    }
}
