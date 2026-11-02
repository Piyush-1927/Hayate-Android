package com.hayate.core.anilist

data class AnimeMedia(
    val id: Int,
    val title: String,
    val coverUrl: String = "",
    val bannerUrl: String = "",
    val description: String = "",
    val format: String? = null,
    val episodes: Int = 1,
    val averageScore: Int? = null,
    val idMal: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val genres: List<String> = emptyList(),
    val isAdult: Boolean = false,
    val relations: List<AnimeRelation> = emptyList(),
    val airingEpisode: Int? = null,
    val airingTime: Long? = null,
    val startYear: Int? = null,
    val startMonth: Int? = null,
    val startDay: Int? = null,
    val endYear: Int? = null,
    val endMonth: Int? = null,
    val endDay: Int? = null,
    val status: String? = null,
    val popularity: Int? = null,
    val synonyms: List<String> = emptyList(),
) {
    val displayTitle: String get() = title.ifBlank { "Unknown Title" }
    val isDateConfirmed: Boolean get() = startYear != null && startMonth != null
    val hasPrequel: Boolean get() = relations.any { it.relationType == "PREQUEL" }
}
