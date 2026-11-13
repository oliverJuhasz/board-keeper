package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import com.webauthn4j.util.exception.WebAuthnException;
import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.AccountRepository.Credentials;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.HttpSessionPublicKeyCredentialCreationOptionsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passkeys: signing in with a phone, a computer or a security key instead of a password. Every
 * Account keeps its password as well; a passkey is only an easier way in.
 *
 * <p>Adding a passkey and signing in with one each take two requests. The first asks the
 * server for options, which hold a random challenge. The browser hands them to the phone, which
 * signs the challenge with the passkey's private key, and the second request brings back what
 * the phone answered. Between the two, the options wait in the session, so the server can check
 * that the answer is to its own, fresh challenge. Spring Security and the WebAuthn4J library
 * do the checking.
 *
 * <p>A passkey belongs to one web address. The installation's address comes from the
 * {@code boardkeeper.url} setting (the BOARDKEEPER_URL environment variable). Without it, passkeys
 * are off.
 */
@Service
class Passkeys {

    private static final Logger log = LoggerFactory.getLogger(Passkeys.class);

    static final int MAX_LABEL_LENGTH = 50;

    private static final String NOT_ADDED = "passkeys.notAdded";
    private static final String NOT_ACCEPTED = "session.passkeyNotAccepted";

    private final Optional<WebAuthnRelyingPartyOperations> operations;
    private final PublicKeyCredentialUserEntityRepository users;
    private final UserCredentialRepository credentials;
    private final AccountRepository accounts;
    private final HttpSessionPublicKeyCredentialCreationOptionsRepository creationOptions =
            new HttpSessionPublicKeyCredentialCreationOptionsRepository();
    private final HttpSessionPublicKeyCredentialRequestOptionsRepository requestOptions =
            new HttpSessionPublicKeyCredentialRequestOptionsRepository();

    Passkeys(
            @Value("${boardkeeper.url:}") String address,
            PublicKeyCredentialUserEntityRepository users,
            UserCredentialRepository credentials,
            AccountRepository accounts) {
        this.users = users;
        this.credentials = credentials;
        this.accounts = accounts;
        this.operations = address.isBlank() ? Optional.empty() : Optional.of(operationsFor(address));
    }

    /**
     * The "relying party" in WebAuthn's words: the website that passkeys are made for. Its id
     * is the host name, and only pages from its own address may use its passkeys.
     */
    private Webauthn4JRelyingPartyOperations operationsFor(String address) {
        URI uri = URI.create(address.strip());
        boolean local = "localhost".equals(uri.getHost());
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))) {
            throw new IllegalStateException("BOARDKEEPER_URL must be Board Keeper's https:// address, such as"
                    + " https://boardkeeper.example.com, but is \"" + address + "\"");
        }
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        PublicKeyCredentialRpEntity site = PublicKeyCredentialRpEntity.builder()
                .id(uri.getHost())
                .name("Board Keeper")
                .build();
        return new Webauthn4JRelyingPartyOperations(users, credentials, site, Set.of(origin));
    }

    boolean available() {
        return operations.isPresent();
    }

    /** The first step of adding a passkey to the signed-in Account. */
    @Transactional
    PublicKeyCredentialCreationOptions startAdding(
            CurrentAccount account, HttpServletRequest request, HttpServletResponse response) {
        // Spring Security finds or creates the Account's WebAuthn user by this name, and a phone
        // shows it when it offers the passkey.
        var named = UsernamePasswordAuthenticationToken.authenticated(account.username(), null, List.of());
        PublicKeyCredentialCreationOptions options =
                operations().createPublicKeyCredentialCreationOptions(new ImmutablePublicKeyCredentialCreationOptionsRequest(named));
        creationOptions.save(request, response, options);
        return options;
    }

    /** The second step: checks what the phone answered and keeps the new passkey. */
    @Transactional
    Passkey add(
            CurrentAccount account,
            PublicKeyCredential<AuthenticatorAttestationResponse> credential,
            String label,
            HttpServletRequest request,
            HttpServletResponse response) {
        WebAuthnRelyingPartyOperations operations = operations();
        PublicKeyCredentialCreationOptions options = creationOptions.load(request);
        // Options are good for one try.
        creationOptions.save(request, response, null);
        if (options == null || !options.getUser().getName().equals(account.username())) {
            throw Problem.unprocessable(NOT_ADDED);
        }
        try {
            CredentialRecord added = operations.registerCredential(new ImmutableRelyingPartyRegistrationRequest(
                    options, new RelyingPartyPublicKey(credential, label.strip())));
            return Passkey.of(added);
        } catch (WebAuthnException | IllegalArgumentException e) {
            log.debug("A passkey was not added", e);
            throw Problem.unprocessable(NOT_ADDED);
        }
    }

    /** The signed-in Account's passkeys, the most recently added first. */
    @Transactional(readOnly = true)
    List<Passkey> of(CurrentAccount account) {
        return userOf(account)
                .map(user -> credentials.findByUserId(user.getId()).stream()
                        .map(Passkey::of)
                        .sorted(Comparator.comparing(Passkey::created, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    void remove(CurrentAccount account, String passkeyId) {
        CredentialRecord passkey = credentials.findByCredentialId(Bytes.fromBase64(passkeyId));
        Optional<PublicKeyCredentialUserEntity> user = userOf(account);
        if (passkey == null || user.isEmpty() || !passkey.getUserEntityUserId().equals(user.get().getId())) {
            throw Problem.notFound("passkeys.notFound");
        }
        credentials.delete(passkey.getCredentialId());
    }

    /**
     * The first step of signing in with a passkey. The options name no Account: the phone
     * offers the passkeys it has for this site, and the one chosen says whose it is.
     */
    PublicKeyCredentialRequestOptions startSignIn(HttpServletRequest request, HttpServletResponse response) {
        PublicKeyCredentialRequestOptions options =
                operations().createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(null));
        requestOptions.save(request, response, options);
        return options;
    }

    /** The second step: checks the passkey's signature and says whose Account it opens. */
    @Transactional
    UUID signIn(
            PublicKeyCredential<AuthenticatorAssertionResponse> credential,
            HttpServletRequest request,
            HttpServletResponse response) {
        WebAuthnRelyingPartyOperations operations = operations();
        PublicKeyCredentialRequestOptions options = requestOptions.load(request);
        requestOptions.save(request, response, null);
        if (options == null) {
            throw new Problem(HttpStatus.UNAUTHORIZED, NOT_ACCEPTED);
        }
        PublicKeyCredentialUserEntity user;
        try {
            user = operations.authenticate(new RelyingPartyAuthenticationRequest(options, credential));
        } catch (WebAuthnException | IllegalArgumentException e) {
            log.debug("A passkey was not accepted", e);
            throw new Problem(HttpStatus.UNAUTHORIZED, NOT_ACCEPTED);
        }
        Credentials account = accounts.findCredentials(user.getName())
                .orElseThrow(() -> new Problem(HttpStatus.UNAUTHORIZED, NOT_ACCEPTED));
        if (account.disabled()) {
            throw new Problem(HttpStatus.UNAUTHORIZED, "session.accountDisabled");
        }
        return account.accountId();
    }

    private WebAuthnRelyingPartyOperations operations() {
        return operations.orElseThrow(() -> Problem.notFound("passkeys.unavailable"));
    }

    private Optional<PublicKeyCredentialUserEntity> userOf(CurrentAccount account) {
        return Optional.ofNullable(users.findByUsername(account.username()));
    }

    /**
     * A passkey as its owner sees it in Settings.
     *
     * @param id the credential id, as base64url text
     */
    record Passkey(String id, String label, Instant created, Instant lastUsed) {

        static Passkey of(CredentialRecord record) {
            // Spring Security counts adding a passkey as using it. Here "last used" means the
            // last sign-in, so a passkey that has never signed in has none.
            Instant lastUsed = record.getLastUsed() == null || record.getLastUsed().equals(record.getCreated())
                    ? null
                    : record.getLastUsed();
            return new Passkey(record.getCredentialId().toBase64UrlString(), record.getLabel(), record.getCreated(), lastUsed);
        }
    }
}
