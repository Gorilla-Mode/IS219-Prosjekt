package no.olbrygging.ugc.account.service

import no.olbrygging.ugc.account.model.AppUser
import no.olbrygging.ugc.account.repository.AppUserRepository
import org.springframework.stereotype.Service

class UsernameAlreadyRegisteredException : RuntimeException("Username already registered.")

@Service
class AccountService(private val users: AppUserRepository) {
    fun register(username: String, password: String): AppUser =
        users.insert(username, password) ?: throw UsernameAlreadyRegisteredException()
}
