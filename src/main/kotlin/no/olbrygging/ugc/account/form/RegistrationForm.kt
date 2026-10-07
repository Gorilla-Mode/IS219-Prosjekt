package no.olbrygging.ugc.account.form

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import no.olbrygging.ugc.account.normalizeUsername

class RegistrationForm {
    @field:NotBlank(message = "Enter your username.")
    @field:Size(max = 254, message = "Use at most 254 characters.")
    var username: String = ""
        set(value) { field = normalizeUsername(value) }

    @field:NotEmpty(message = "Enter a password.")
    @field:Size(max = 200, message = "Use at most 200 characters.")
    var password: String = ""

    fun clearPassword() {
        password = ""
    }
}
