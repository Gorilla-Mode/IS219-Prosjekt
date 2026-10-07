package no.olbrygging.ugc.account.service

import no.olbrygging.ugc.account.normalizeEmail
import no.olbrygging.ugc.account.model.AppUser
import no.olbrygging.ugc.account.repository.AppUserRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

class EmailAlreadyRegisteredException : RuntimeException("Email already registered.")

@Service
class AccountService(private val users: AppUserRepository) {
    fun findByEmail(email: String): AppUser? = users.findByEmail(normalizeEmail(email))

    fun register(email: String, displayName: String, password: String): AppUser = try {
        users.save(AppUser(email = normalizeEmail(email), displayName = displayName.trim(), password = password))
    } catch (_: DuplicateKeyException) {
        throw EmailAlreadyRegisteredException()
    }
}
