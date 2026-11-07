package com.example.shiro

enum class VisualizerMode(val title: String, val description: String, val selectable: Boolean = true) {
    SPECTRUM("Spectrum", "Glass bars with slow peak trails."),
    LIQUID("Liquid", "Smooth, layered glass waves."),
    HALO("Halo", "A glowing ring that pulls, swirls and releases the Shiros.", selectable = false),
    RIPPLES("Ripples", "Overlapping rings spreading outward with each beat.", selectable = false),
    ORBITS("Orbits", "Intertwined light trails twisting with the music.", selectable = false),
    RESONANCE("Resonance", "Glowing patterns with the same pull, swirl and release as Halo.", selectable = false),
    PLAYGROUND("Glass playground", "Floating islands, soft cushions and piano keys over a liquid ribbon.", selectable = false);

    val isAmbient: Boolean get() = this == HALO || this == RIPPLES || this == ORBITS || this == RESONANCE
    val hasCenterField: Boolean get() = this == HALO || this == RESONANCE
    val hasCurvedSurface: Boolean get() = this == LIQUID || this == PLAYGROUND

    companion object {
        val available = entries.filter { it.selectable }
        fun fromPreference(value: String?): VisualizerMode = available.firstOrNull { it.name == value } ?: SPECTRUM
    }
}

enum class VisualizerLayout(val title: String) {
    FLOOR("Floor"), CIRCLE("Circle");

    companion object {
        fun fromPreference(value: String?, previousMode: String? = null): VisualizerLayout =
            entries.firstOrNull { it.name == value }
                ?: if (previousMode == "HALO" || previousMode == "RESONANCE") CIRCLE else FLOOR
    }
}
