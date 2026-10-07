package no.olbrygging.ugc

import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import no.olbrygging.ugc.account.repository.AppUserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppUserRepositoryTests {
    @Test
    fun `a new store loses registered accounts and recreates test credentials`() {
        val oldStore = AppUserRepository()
        oldStore.insert("registered", "password")
        assertThat(oldStore.count()).isEqualTo(2)

        val startedAt = Instant.now()
        val newStore = AppUserRepository()
        assertThat(newStore.findByUsername("registered")).isNull()
        assertThat(newStore.count()).isEqualTo(1)
        val seed = newStore.findByUsername("test")!!
        assertThat(seed.username).isEqualTo("test")
        assertThat(seed.password).isEqualTo("test")
        assertThat(seed.id).isPositive()
        assertThat(seed.createdAt).isBetween(startedAt, Instant.now())
    }

    @Test
    fun `insertion and lookup normalize usernames without overwriting existing credentials`() {
        val users = AppUserRepository()
        val user = users.insert(" User ", "original")!!
        assertThat(user.username).isEqualTo("user")
        assertThat(users.insert("USER", "replacement")).isNull()
        assertThat(users.findByUsername(" USER ")).isEqualTo(user)
        assertThat(users.count()).isEqualTo(2)
    }

    @Test
    fun `concurrent registrations of distinct users have unique generated IDs`() {
        val users = AppUserRepository()
        Executors.newFixedThreadPool(4).use { executor ->
            val attempts = (1..40).map { index ->
                executor.submit(Callable { users.insert("user$index", "password")!! })
            }
            val registered = attempts.map { it.get(10, TimeUnit.SECONDS) }
            assertThat(registered.map { it.id }).doesNotHaveDuplicates().allMatch { it > 0 }
            assertThat(registered.map { it.id }).doesNotContain(users.findByUsername("test")!!.id)
            registered.forEach { assertThat(users.findByUsername(it.username)).isEqualTo(it) }
        }
        assertThat(users.count()).isEqualTo(41)
    }
}
