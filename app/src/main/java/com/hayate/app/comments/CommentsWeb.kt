package com.hayate.app.comments

object CommentsWeb {
    const val PAGE_URL = "https://piyush-1927.github.io/hayate-comments/"
    const val REQUEST_EMAIL = "neonblade0018@gmail.com"

    // Same map as desktop core/comments_web.py: AniList id -> season discussion number
    private val DISCUSSIONS = mapOf(21355 to 1)

    fun pageUrl(anilistId: Int, episode: Int?, dark: Boolean = true): String? {
        val n = DISCUSSIONS[anilistId] ?: return null
        val theme = if (dark) "transparent_dark" else "noborder_light"
        return "$PAGE_URL?n=$n&theme=$theme&a=$anilistId&ep=${episode ?: ""}"
    }

    fun requestSubject(id: Int?) = "Hayate: please start a discussion for AniList id ${id ?: "unknown"}"

    fun requestBody(id: Int?): String {
        val a = id?.toString() ?: "unknown"
        return "Hi, I'm a Hayate user and I'd like a comments discussion thread to be created for this anime " +
            "so viewers can discuss episodes there. AniList id: $a - AniList link: https://anilist.co/anime/$a - Thank you!"
    }
}
