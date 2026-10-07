package no.olbrygging.ugc.config

import no.olbrygging.ugc.account.service.AccountUserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfiguration {
    @Bean
    @Suppress("DEPRECATION")
    fun authenticationProvider(users: AccountUserDetailsService): DaoAuthenticationProvider =
        DaoAuthenticationProvider(users).apply { setPasswordEncoder(NoOpPasswordEncoder.getInstance()) }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { it.requestMatchers("/register", "/login", "/error").permitAll().anyRequest().authenticated() }
            .formLogin {
                it.loginPage("/login").usernameParameter("email")
                    .defaultSuccessUrl("/", true).failureUrl("/login?error").permitAll()
            }
            .logout { it.logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID") }
        return http.build()
    }
}
