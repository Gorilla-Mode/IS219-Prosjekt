package no.olbrygging.ugc

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import no.olbrygging.ugc.account.form.RegistrationForm
import no.olbrygging.ugc.account.repository.AppUserRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint
import org.springframework.context.ApplicationContext
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.BindingResult

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountIntegrationTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: AppUserRepository
    @Autowired lateinit var context: ApplicationContext

    private fun register(
        username: String = "user",
        password: String = "password123",
    ): MvcResult = mvc.perform(
        post("/register").with(csrf())
            .param("username", username).param("password", password),
    ).andReturn()

    private fun login(username: String = "user", password: String = "password123"): MvcResult =
        mvc.perform(post("/login").with(csrf()).param("username", username).param("password", password)).andReturn()

    @Test
    fun `registration normalizes username and stores account fields`() {
        val result = register("  User  ")
        assertThat(result.response.redirectedUrl).isEqualTo("/login?registered")
        val user = users.findByUsername("user")!!
        assertThat(user.id).isPositive()
        assertThat(user.username).isEqualTo("user")
        assertThat(user.password).isEqualTo("password123")
        assertThat(user.createdAt).isNotNull()
        assertThat(user.toString()).doesNotContain("password123")
    }

    @Test
    fun `login uses repository and normalizes username and home escapes it`() {
        register(username = "<b>Test User</b>")
        val result = login("  <B>TEST USER</B>  ")
        assertThat(result.response.redirectedUrl).isEqualTo("/")
        authenticated().withUsername("<b>test user</b>").match(result)
        mvc.perform(get("/").session(result.request.getSession(false) as MockHttpSession))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("&lt;b&gt;test user&lt;/b&gt;")))
            .andExpect(content().string(containsString("href=\"/css/app.css\"")))
            .andExpect(content().string(containsString("name=\"viewport\" content=\"width=device-width, initial-scale=1\"")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
    }

    @Test
    fun `fresh store includes the test account and it can log in`() {
        val user = users.findByUsername("test")!!
        assertThat(user.password).isEqualTo("test")
        assertThat(user.id).isPositive()
        assertThat(user.createdAt).isNotNull()
        val result = login("test", "test")
        assertThat(result.response.redirectedUrl).isEqualTo("/")
        authenticated().withUsername("test").match(result)
        mvc.perform(get("/").session(result.request.getSession(false) as MockHttpSession))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("<span>test</span>")))
    }

    @Test
    fun `one character credentials and maximum lengths can register and log in`() {
        for ((username, password) in listOf("u" to "p", "u".repeat(254) to "p".repeat(200))) {
            assertThat(register(username, password).response.redirectedUrl).isEqualTo("/login?registered")
            val result = login(username, password)
            assertThat(result.response.redirectedUrl).isEqualTo("/")
            authenticated().withUsername(username).match(result)
        }
    }

    @Test
    fun `passwords are preserved exactly including whitespace`() {
        for ((username, password) in listOf("spaces" to " p ", "space" to " ")) {
            assertThat(register(username, password).response.redirectedUrl).isEqualTo("/login?registered")
            assertThat(users.findByUsername(username)!!.password).isEqualTo(password)
            authenticated().withUsername(username).match(login(username, password))
            assertThat(login(username, password.trim()).response.redirectedUrl).isEqualTo("/login?error")
        }
    }

    @Test
    fun `invalid login is generic for wrong password and missing account`() {
        register()
        for ((username, password) in listOf("user" to "wrong", "missing" to "password123", "" to "password123", "user" to "")) {
            val result = login(username, password)
            assertThat(result.response.redirectedUrl).isEqualTo("/login?error")
            unauthenticated().match(result)
        }
        mvc.perform(get("/login?error"))
            .andExpect(content().string(containsString("Invalid username or password.")))
    }

    @Test
    fun `home requires authentication and public forms include csrf`() {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection)
        mvc.perform(get("/css/app.css")).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("text/css"))
        for (path in listOf("/login", "/register")) {
            mvc.perform(get(path)).andExpect(status().isOk)
                .andExpect(content().string(containsString("href=\"/css/app.css\"")))
                .andExpect(content().string(containsString("name=\"viewport\" content=\"width=device-width, initial-scale=1\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(not(containsString("name=\"email\""))))
                .andExpect(content().string(not(containsString("name=\"displayName\""))))
                .andExpect(content().string(not(containsString("name=\"passwordConfirmation\""))))
                .andExpect(content().string(not(containsString("minlength="))))
        }
    }

    @Test
    fun `state changing requests require csrf`() {
        for (path in listOf("/register", "/login", "/logout")) {
            mvc.perform(post(path)).andExpect(status().isForbidden)
            mvc.perform(post(path).with(csrf().useInvalidToken())).andExpect(status().isForbidden)
        }
        assertThat(users.count()).isEqualTo(1)
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
            "" to "password123",
            "   " to "password123",
            "u".repeat(255) to "password123",
            "user" to "",
            "user" to "p".repeat(201),
        )
        for ((username, password) in cases) {
            val result = register(username, password)
            assertThat(result.response.status).isEqualTo(200)
            assertThat(result.modelAndView!!.viewName).isEqualTo("register")
            val form = result.modelAndView!!.model["registrationForm"] as RegistrationForm
            assertThat(form.password).isEmpty()
            val binding = result.modelAndView!!.model[BindingResult.MODEL_KEY_PREFIX + "registrationForm"] as? BindingResult
            if (binding != null) {
                assertThat(binding.getFieldValue("password")).isEqualTo("")
            }
            if (password.isNotEmpty()) assertThat(result.response.contentAsString).doesNotContain(password)
            assertThat(result.modelAndView!!.model["formErrors"]).isNotNull()
        }
        assertThat(users.count()).isEqualTo(1)
    }

    @Test
    fun `duplicate username is a form error with empty password`() {
        register()
        val duplicate = register(" USER ")
        assertThat(duplicate.response.status).isEqualTo(200)
        assertThat(duplicate.response.contentAsString).contains("This username is already registered.").doesNotContain("password123")
        assertThat((duplicate.modelAndView!!.model["registrationForm"] as RegistrationForm).password).isEmpty()
        assertThat(users.count()).isEqualTo(2)
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
                    register(if (it == 1) " Concurrent " else "CONCURRENT")
                })
            }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val results = attempts.map { it.get(20, TimeUnit.SECONDS) }
            assertThat(results.map { it.response.status }).containsExactlyInAnyOrder(302, 200)
            assertThat(results.single { it.response.status == 200 }.response.contentAsString)
                .contains("This username is already registered.").doesNotContain("password123")
        }
        assertThat(users.count()).isEqualTo(2)
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
