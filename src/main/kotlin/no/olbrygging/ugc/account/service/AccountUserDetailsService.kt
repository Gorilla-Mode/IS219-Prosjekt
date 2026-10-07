package no.olbrygging.ugc.account.service

import no.olbrygging.ugc.account.normalizeUsername
import no.olbrygging.ugc.account.repository.AppUserRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AccountUserDetailsService(private val users: AppUserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = users.findByUsername(normalizeUsername(username))
            ?: throw UsernameNotFoundException("Invalid username or password.")
        return User.withUsername(user.username).password(user.password).roles("USER").build()
    }
}
