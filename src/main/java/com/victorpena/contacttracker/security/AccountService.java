package com.victorpena.contacttracker.security;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return jdbc.query("SELECT * FROM app_users WHERE email = ?", (rs, row) -> {
            var until = rs.getTimestamp("locked_until");
            return User.withUsername(rs.getString("email"))
                    .password(rs.getString("password_hash"))
                    .roles("USER")
                    .disabled(!rs.getBoolean("enabled"))
                    .accountLocked(until != null && until.toInstant().isAfter(Instant.now()))
                    .build();
        }, normalize(email)).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Sign-in failed"));
    }

    /** Creates only a missing account. Redeploying never resets an existing password. */
    public boolean createIfMissing(String rawEmail, String password) {
        String email = normalize(rawEmail);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("APP_BOOTSTRAP_EMAIL must be a valid email address");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE email = ?", Integer.class, email) > 0) {
            return false;
        }
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Bootstrap password must have at least 12 characters and at most 72 UTF-8 bytes");
        }
        String hash = encoder.encode(password);
        try {
            jdbc.update("INSERT INTO app_users (email, password_hash) VALUES (?, ?)", email, hash);
            return true;
        } catch (DuplicateKeyException alreadyCreatedByAnotherInstance) {
            return false;
        }
    }

    @Transactional
    public void recordFailure(String rawEmail) {
        String email = normalize(rawEmail);
        var states = jdbc.query("SELECT failed_login_attempts, locked_until FROM app_users WHERE email = ? FOR UPDATE",
                (rs, row) -> new LoginState(rs.getInt(1), rs.getTimestamp(2)), email);
        if (states.isEmpty()) return;
        LoginState state = states.get(0);
        Instant now = Instant.now();
        if (state.until() != null && state.until().toInstant().isAfter(now)) return;
        int attempts = state.until() == null ? state.attempts() + 1 : 1;
        Timestamp until = attempts >= 8 ? Timestamp.from(now.plus(15, ChronoUnit.MINUTES)) : null;
        jdbc.update("UPDATE app_users SET failed_login_attempts = ?, locked_until = ? WHERE email = ?", attempts, until, email);
    }

    public void recordSuccess(String email) {
        jdbc.update("UPDATE app_users SET failed_login_attempts = 0, locked_until = NULL WHERE email = ?", normalize(email));
    }

    private record LoginState(int attempts, Timestamp until) {}
}
