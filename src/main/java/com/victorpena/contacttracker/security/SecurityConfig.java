package com.victorpena.contacttracker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountService accounts) throws Exception {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwordEncoder());
        var login = new LoginUrlAuthenticationEntryPoint("/login");
        http.authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/login", "/login.css", "/healthz").permitAll()
                .anyRequest().authenticated())
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .formLogin(form -> form.loginPage("/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    accounts.recordSuccess(authentication.getName());
                    response.sendRedirect(request.getContextPath() + "/index.html");
                })
                .failureHandler((request, response, exception) -> {
                    accounts.recordFailure(request.getParameter("email"));
                    response.sendRedirect(request.getContextPath() + "/login?error");
                }).permitAll())
            .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"))
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Please sign in to continue.\"}");
                } else {
                    login.commence(request, response, exception);
                }
            }).accessDeniedHandler((request, response, exception) -> {
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Request could not be verified. Reload the page and sign in again.\"}");
            }))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny()));
        // CSRF protection and session fixation protection remain enabled.
        return http.build();
    }
}
