package no.olbrygging.ugc.account

import java.util.Locale

fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
