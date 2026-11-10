package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The one-time link for creating the first Admin. While no Account exists, a new link is
 * written to the log every time the application starts. Whoever can read the log can set
 * the installation up; nobody else can.
 */
@Component
class SetupLink {

    private static final Logger log = LoggerFactory.getLogger(SetupLink.class);

    private final AccountRepository accounts;
    private final SecureRandom random = new SecureRandom();
    private String token;

    SetupLink(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @EventListener(ApplicationReadyEvent.class)
    void offerWhenThereAreNoAccounts() {
        if (!accounts.anyExist()) {
            offer();
        }
    }

    /** Makes a new link, replacing any earlier one, and writes it to the log. */
    synchronized String offer() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.info("""

                ------------------------------------------------------------------
                Board Keeper has no Accounts yet.
                To create the first Admin, open this page on Board Keeper's address:

                    /setup?token={}

                A new link is made every time the application starts, until the
                first Admin exists.
                ------------------------------------------------------------------
                """, token);
        return token;
    }

    /** Uses the link up if the token is the right one. A link works only once. */
    synchronized boolean claim(String candidate) {
        if (token == null || candidate == null) {
            return false;
        }
        // Compares in a way that takes the same time wherever the first difference is.
        boolean matches = MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
        if (matches) {
            token = null;
        }
        return matches;
    }

    /** Puts a claimed link back, for when the setup it was claimed for failed. */
    synchronized void restore(String claimed) {
        token = claimed;
    }
}
