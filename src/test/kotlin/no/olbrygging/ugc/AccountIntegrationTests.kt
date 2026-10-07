package no.olbrygging.ugc

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import no.olbrygging.ugc.account.form.RegistrationForm
import no.olbrygging.ugc.account.repository.AppUserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint
import org.springframework.context.ApplicationContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.BindingResult
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AccountIntegrationTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: AppUserRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var context: ApplicationContext

    companion object {
        private val postgres = PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("ugc")
            .withUsername("ugc")
            .withPassword("ugc")
            .withInitScript("01-schema.sql")
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @BeforeEach
    fun cleanAccounts() { users.deleteAll() }

    private fun register(
        email: String = "user@example.com",
        name: String = "Test User",
        password: String = "password123",
        confirmation: String = password,
    ): MvcResult = mvc.perform(
        post("/register").with(csrf())
            .param("email", email).param("displayName", name)
            .param("password", password).param("passwordConfirmation", confirmation),
    ).andReturn()

    private fun login(email: String = "user@example.com", password: String = "password123"): MvcResult =
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password)).andReturn()

    @Test
    fun `registration normalizes email and name and stores the schema fields`() {
        assertThat(register("  User@Example.COM  ", "  Test User  ").response.redirectedUrl)
            .isEqualTo("/login?registered")
        val user = users.findByEmail("user@example.com")!!
        assertThat(user.id).isPositive()
        assertThat(user.displayName).isEqualTo("Test User")
        assertThat(user.password).isEqualTo("password123")
        assertThat(user.createdAt).isNotNull()
        assertThat(user.toString()).doesNotContain("password123")
    }

    @Test
    fun `login uses repository and normalizes email and home shows escaped name`() {
        register(name = "<b>Test User</b>")
        val result = login("  USER@EXAMPLE.COM  ")
        assertThat(result.response.redirectedUrl).isEqualTo("/")
        authenticated().withUsername("user@example.com").match(result)
        mvc.perform(get("/").session(result.request.getSession(false) as MockHttpSession))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("&lt;b&gt;Test User&lt;/b&gt;")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
    }

    @Test
    fun `invalid login is generic for wrong password and missing account`() {
        register()
        for ((email, password) in listOf("user@example.com" to "wrong", "missing@example.com" to "password123")) {
            val result = login(email, password)
            assertThat(result.response.redirectedUrl).isEqualTo("/login?error")
            unauthenticated().match(result)
        }
        mvc.perform(get("/login?error"))
            .andExpect(content().string(containsString("Invalid email or password.")))
    }

    @Test
    fun `home requires authentication and public forms include csrf`() {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection)
        for (path in listOf("/login", "/register")) {
            mvc.perform(get(path)).andExpect(status().isOk)
                .andExpect(content().string(containsString("name=\"_csrf\"")))
        }
    }

    @Test
    fun `state changing requests require csrf`() {
        for (path in listOf("/register", "/login", "/logout")) {
            mvc.perform(post(path)).andExpect(status().isForbidden)
            mvc.perform(post(path).with(csrf().useInvalidToken())).andExpect(status().isForbidden)
        }
        assertThat(users.count()).isZero()
    }

    @Test
    fun `logout is post only and invalidates the session`() {
        register()
        val session = login().request.getSession(false) as MockHttpSession
        mvc.perform(get("/logout").session(session)).andExpect(status().isNotFound)
        mvc.perform(get("/").session(session)).andExpect(status().isOk)
        mvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated())
        assertThat(session.isInvalid).isTrue()
        mvc.perform(get("/")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `invalid forms do not persist accounts or retain passwords`() {
        val cases = listOf(
            arrayOf("", "Test User", "password123", "password123"),
            arrayOf("invalid", "Test User", "password123", "password123"),
            arrayOf("a".repeat(250) + "@example.com", "Test User", "password123", "password123"),
            arrayOf("user@example.com", "   ", "password123", "password123"),
            arrayOf("user@example.com", "n".repeat(101), "password123", "password123"),
            arrayOf("user@example.com", "Test User", "", ""),
            arrayOf("user@example.com", "Test User", "short", "short"),
            arrayOf("user@example.com", "Test User", "p".repeat(201), "p".repeat(201)),
            arrayOf("user@example.com", "Test User", "password123", "different123"),
        )
        for (values in cases) {
            val result = register(values[0], values[1], values[2], values[3])
            assertThat(result.response.status).isEqualTo(200)
            assertThat(result.modelAndView!!.viewName).isEqualTo("register")
            val form = result.modelAndView!!.model["registrationForm"] as RegistrationForm
            assertThat(form.password).isEmpty()
            assertThat(form.passwordConfirmation).isEmpty()
            val binding = result.modelAndView!!.model[BindingResult.MODEL_KEY_PREFIX + "registrationForm"] as? BindingResult
            if (binding != null) {
                assertThat(binding.getFieldValue("password")).isEqualTo("")
                assertThat(binding.getFieldValue("passwordConfirmation")).isEqualTo("")
            }
            if (values[2].isNotEmpty()) assertThat(result.response.contentAsString).doesNotContain(values[2])
            assertThat(result.modelAndView!!.model["formErrors"]).isNotNull()
        }
        assertThat(users.count()).isZero()
    }

    @Test
    fun `duplicate email is a form error with empty passwords`() {
        register()
        val duplicate = register(" USER@EXAMPLE.COM ")
        assertThat(duplicate.response.status).isEqualTo(200)
        assertThat(duplicate.response.contentAsString).contains("This email is already registered.").doesNotContain("password123")
        assertThat(users.count()).isEqualTo(1)
    }

    @Test
    fun `concurrent duplicate registrations yield one success and one form error`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val attempts = (1..2).map {
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    register("concurrent@example.com")
                })
            }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val results = attempts.map { it.get(20, TimeUnit.SECONDS) }
            assertThat(results.map { it.response.status }).containsExactlyInAnyOrder(302, 200)
            assertThat(results.single { it.response.status == 200 }.response.contentAsString)
                .contains("This email is already registered.").doesNotContain("password123")
        }
        assertThat(users.count()).isEqualTo(1)
    }

    @Test
    fun `database enforces null length and normalization constraints`() {
        for ((email, name, password) in listOf(
            Triple("Upper@example.com", "User", "password123"),
            Triple(" user@example.com ", "User", "password123"),
            Triple("user@example.com", " ", "password123"),
            Triple("user@example.com", "x".repeat(101), "password123"),
            Triple("user@example.com", "User", "short"),
            Triple("user@example.com", "User", "p".repeat(201)),
            Triple(null, "User", "password123"),
            Triple("user@example.com", null, "password123"),
            Triple("user@example.com", "User", null),
        )) {
            assertThatThrownBy {
                jdbc.update("INSERT INTO app_users (email, display_name, password) VALUES (?, ?, ?)", email, name, password)
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }
    }

    @Test
    fun `startup requires no AI model or credentials`() {
        assertThat(context.getBeansOfType(ChatModel::class.java)).isEmpty()
        assertThat(context.environment.getProperty("spring.ai.model.chat")).isEqualTo("none")
        assertThat(context.environment.getProperty("spring.ai.ollama.init.pull-model-strategy")).isEqualTo("never")
        mvc.perform(get("/login")).andExpect(status().isOk)
            .andExpect(content().string(not(containsString("password123"))))
    }
}
