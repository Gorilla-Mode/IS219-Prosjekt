package no.olbrygging.ugc.account.controller

import jakarta.validation.Valid
import no.olbrygging.ugc.account.form.RegistrationForm
import no.olbrygging.ugc.account.service.AccountService
import no.olbrygging.ugc.account.service.UsernameAlreadyRegisteredException
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping

@Controller
class AccountController(private val accounts: AccountService) {
    @GetMapping("/register")
    fun register(model: Model): String {
        model.addAttribute("registrationForm", RegistrationForm())
        return "register"
    }

    @PostMapping("/register")
    fun register(
        @Valid @ModelAttribute("registrationForm") form: RegistrationForm,
        errors: BindingResult,
        model: Model,
    ): String {
        if (!errors.hasErrors()) {
            try {
                accounts.register(form.username, form.password)
                form.clearPassword()
                return "redirect:/login?registered"
            } catch (_: UsernameAlreadyRegisteredException) {
                errors.rejectValue("username", "duplicate", "This username is already registered.")
            }
        }
        // BindingResult can retain rejected password values; expose only safe messages.
        val messages = errors.fieldErrors.groupBy({ it.field }, { it.defaultMessage ?: "Invalid value." })
        form.clearPassword()
        model.asMap().remove(BindingResult.MODEL_KEY_PREFIX + "registrationForm")
        model.addAttribute("formErrors", messages)
        return "register"
    }

    @GetMapping("/login")
    fun login(): String = "login"
}
