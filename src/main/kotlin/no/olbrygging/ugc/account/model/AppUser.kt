package no.olbrygging.ugc.account.model

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("app_users")
data class AppUser(
    @Id val id: Long? = null,
    val email: String,
    val displayName: String,
    val password: String,
    val createdAt: Instant = Instant.now(),
) {
    override fun toString() = "AppUser(id=$id)"
}
