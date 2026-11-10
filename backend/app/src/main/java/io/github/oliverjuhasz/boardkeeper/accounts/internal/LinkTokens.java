package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The secret part of a one-time link, such as an Invite's. The token itself goes into the link
 * and is shown once. The database keeps only its hash, which is enough to recognise the token
 * when the link is opened, but cannot be turned back into it.
 */
final class LinkTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkTokens() {}

    /** A new token: 32 random bytes in a form that is safe inside a web address. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
