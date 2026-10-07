package no.olbrygging.ugc.home

import java.security.Principal
import no.olbrygging.ugc.account.service.AccountService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController(private val accounts: AccountService) {
    @GetMapping("/")
    fun home(principal: Principal, model: Model): String {
        model.addAttribute("displayName", accounts.findByEmail(principal.name)?.displayName)
        return "home"
    }
}
