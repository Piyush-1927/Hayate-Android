package com.hayate.app

/** OAuth Desktop client, supplied at build time (local.properties or env vars). Empty = Google sign-in disabled. */
internal object GoogleCreds {
    val ID: String = BuildConfig.GOOGLE_CLIENT_ID
    val SECRET: String = BuildConfig.GOOGLE_CLIENT_SECRET
    val configured: Boolean get() = ID.isNotBlank() && SECRET.isNotBlank()
}
