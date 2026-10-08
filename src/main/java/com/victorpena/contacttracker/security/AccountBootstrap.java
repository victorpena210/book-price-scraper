package com.victorpena.contacttracker.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AccountBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AccountBootstrap.class);
    private final AccountService accounts;
    private final String email;
    private String password;

    public AccountBootstrap(AccountService accounts,
            @Value("${APP_BOOTSTRAP_EMAIL:}") String email,
            @Value("${APP_BOOTSTRAP_PASSWORD:}") String password) {
        this.accounts = accounts;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (email.isBlank() && password.isEmpty()) return;
            if (email.isBlank() || password.isEmpty()) {
                throw new IllegalStateException("Set both APP_BOOTSTRAP_EMAIL and APP_BOOTSTRAP_PASSWORD to create an account");
            }
            if (accounts.createIfMissing(email, password)) {
                log.info("Sign-in account created. Remove APP_BOOTSTRAP_EMAIL and APP_BOOTSTRAP_PASSWORD from the deployment variables.");
            }
        } finally {
            password = null;
        }
    }
}
