package no.olbrygging.ugc.account.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import no.olbrygging.ugc.account.model.AppUser
import no.olbrygging.ugc.account.normalizeUsername
import org.springframework.stereotype.Repository

@Repository
class AppUserRepository {
    private val users = ConcurrentHashMap<String, AppUser>()
    private val ids = AtomicLong()

    init {
        insert("test", "test")
    }

    fun findByUsername(username: String): AppUser? = users[normalizeUsername(username)]

    // A null result means the normalized username already exists.
    fun insert(username: String, password: String): AppUser? {
        val user = AppUser(id = ids.incrementAndGet(), username = normalizeUsername(username), password = password)
        return if (users.putIfAbsent(user.username, user) == null) user else null
    }

    fun count(): Long = users.size.toLong()
}
