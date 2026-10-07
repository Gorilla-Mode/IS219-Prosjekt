package no.olbrygging.ugc.home

import java.security.Principal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {
    @GetMapping("/")
    fun home(principal: Principal, model: Model): String {
        model.addAttribute("username", principal.name)
        return "home"
    }
}
