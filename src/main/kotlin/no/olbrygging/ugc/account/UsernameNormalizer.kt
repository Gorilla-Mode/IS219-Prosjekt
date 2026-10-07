package no.olbrygging.ugc.account

import java.util.Locale

fun normalizeUsername(username: String): String = username.trim().lowercase(Locale.ROOT)
