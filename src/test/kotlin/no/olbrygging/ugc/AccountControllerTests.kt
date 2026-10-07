package no.olbrygging.ugc

import no.olbrygging.ugc.account.controller.AccountController
import no.olbrygging.ugc.account.form.RegistrationForm
import no.olbrygging.ugc.account.repository.AppUserRepository
import no.olbrygging.ugc.account.service.AccountService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ui.ExtendedModelMap
import org.springframework.validation.BeanPropertyBindingResult

class AccountControllerTests {
    @Test
    fun `successful registration clears the form password before redirecting`() {
        val users = AppUserRepository()
        val controller = AccountController(AccountService(users))
        val form = RegistrationForm().apply {
            username = "user"
            password = "password123"
        }
        val errors = BeanPropertyBindingResult(form, "registrationForm")

        assertThat(controller.register(form, errors, ExtendedModelMap())).isEqualTo("redirect:/login?registered")
        assertThat(form.password).isEmpty()
        assertThat(users.findByUsername("user")!!.password).isEqualTo("password123")
    }
}
