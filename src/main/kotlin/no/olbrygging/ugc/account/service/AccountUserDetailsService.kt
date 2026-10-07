package no.olbrygging.ugc.account.service

import no.olbrygging.ugc.account.normalizeEmail
import no.olbrygging.ugc.account.repository.AppUserRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AccountUserDetailsService(private val users: AppUserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = users.findByEmail(normalizeEmail(username))
            ?: throw UsernameNotFoundException("Invalid email or password.")
        return User.withUsername(user.email).password(user.password).roles("USER").build()
    }
}
