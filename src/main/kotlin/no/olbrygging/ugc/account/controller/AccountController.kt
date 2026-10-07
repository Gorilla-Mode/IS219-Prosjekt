package no.olbrygging.ugc.account.controller

import jakarta.validation.Valid
import no.olbrygging.ugc.account.form.RegistrationForm
import no.olbrygging.ugc.account.service.AccountService
import no.olbrygging.ugc.account.service.EmailAlreadyRegisteredException
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
        if (form.password != form.passwordConfirmation) {
            errors.rejectValue("passwordConfirmation", "mismatch", "Passwords must match.")
        }
        if (!errors.hasErrors()) {
            try {
                accounts.register(form.email, form.displayName, form.password)
                form.clearPasswords()
                return "redirect:/login?registered"
            } catch (_: EmailAlreadyRegisteredException) {
                errors.rejectValue("email", "duplicate", "This email is already registered.")
            }
        }
        // BindingResult can retain rejected password values; expose only safe messages.
        val messages = errors.fieldErrors.groupBy({ it.field }, { it.defaultMessage ?: "Invalid value." })
        form.clearPasswords()
        model.asMap().remove(BindingResult.MODEL_KEY_PREFIX + "registrationForm")
        model.addAttribute("formErrors", messages)
        return "register"
    }

    @GetMapping("/login")
    fun login(): String = "login"
}
