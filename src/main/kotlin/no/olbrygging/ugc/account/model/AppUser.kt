package no.olbrygging.ugc.account.model

import java.time.Instant

data class AppUser(
    val id: Long,
    val username: String,
    val password: String,
    val createdAt: Instant = Instant.now(),
) {
    override fun toString() = "AppUser(id=$id)"
}
