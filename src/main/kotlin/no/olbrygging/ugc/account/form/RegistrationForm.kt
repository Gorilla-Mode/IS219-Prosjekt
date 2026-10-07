package no.olbrygging.ugc.account.form

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import no.olbrygging.ugc.account.normalizeEmail

class RegistrationForm {
    @field:NotBlank(message = "Enter your name.")
    @field:Size(max = 100, message = "Use at most 100 characters.")
    var displayName: String = ""
        set(value) { field = value.trim() }

    @field:NotBlank(message = "Enter your email.")
    @field:Email(message = "Enter a valid email.")
    @field:Size(max = 254, message = "Use at most 254 characters.")
    var email: String = ""
        set(value) { field = normalizeEmail(value) }

    @field:NotBlank(message = "Enter a password.")
    @field:Size(min = 8, max = 200, message = "Use between 8 and 200 characters.")
    var password: String = ""

    @field:NotBlank(message = "Confirm your password.")
    @field:Size(max = 200, message = "Use at most 200 characters.")
    var passwordConfirmation: String = ""

    fun clearPasswords() {
        password = ""
        passwordConfirmation = ""
    }
}
