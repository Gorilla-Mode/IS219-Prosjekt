package no.olbrygging.ugc.account.repository

import no.olbrygging.ugc.account.model.AppUser
import org.springframework.data.repository.CrudRepository

interface AppUserRepository : CrudRepository<AppUser, Long> {
    fun findByEmail(email: String): AppUser?
}
