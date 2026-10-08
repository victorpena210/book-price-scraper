package com.victorpena.contacttracker.security;

import java.security.Principal;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SessionController {
    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/healthz")
    @ResponseBody
    public Map<String, String> health() { return Map.of("status", "ok"); }

    @GetMapping("/api/session")
    @ResponseBody
    public SessionInfo session(Principal principal, CsrfToken token) {
        return new SessionInfo(principal.getName(), token.getHeaderName(), token.getToken());
    }

    public record SessionInfo(String email, String csrfHeader, String csrfToken) {}
}
