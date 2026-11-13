package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.webauthn.PackedAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A pretend phone with a browser, for tests: it makes and uses passkeys the way a real one does,
 * signing with real keys. WebAuthn4J's emulated authenticator does the work; this class only
 * translates between the JSON our API speaks and the emulator's Java types, as a browser's
 * {@code parseCreationOptionsFromJSON} and {@code toJSON()} would.
 */
final class TestPhone {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder FROM_BASE64URL = Base64.getUrlDecoder();

    private final ClientPlatform browser;
    /** The ids of the passkeys this phone has made, which it offers when asked to sign in. */
    private final List<byte[]> passkeyIds = new ArrayList<>();

    /** @param origin the address the browser shows, such as http://localhost:5173 */
    TestPhone(String origin) {
        browser = new ClientPlatform(new Origin(origin), new WebAuthnAuthenticatorAdaptor(new PackedAuthenticator()));
    }

    /** What {@code navigator.credentials.create()} answers to options from our API, as JSON. */
    String create(String optionsJson) {
        JsonNode options = JSON.readTree(optionsJson);
        List<PublicKeyCredentialParameters> algorithms = new ArrayList<>();
        options.get("pubKeyCredParams").forEach(parameter -> algorithms.add(new PublicKeyCredentialParameters(
                PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.create(parameter.get("alg").asLong()))));
        JsonNode user = options.get("user");
        JsonNode selection = options.path("authenticatorSelection");
        PublicKeyCredential<AuthenticatorAttestationResponse, ?> created = browser.create(new PublicKeyCredentialCreationOptions(
                new PublicKeyCredentialRpEntity(options.get("rp").get("id").asString(), options.get("rp").get("name").asString()),
                new PublicKeyCredentialUserEntity(
                        bytes(user.get("id")), user.get("name").asString(), user.get("displayName").asString()),
                new DefaultChallenge(bytes(options.get("challenge"))),
                algorithms,
                null,
                List.of(),
                new AuthenticatorSelectionCriteria(
                        null,
                        ResidentKeyRequirement.create(selection.path("residentKey").asString("preferred")),
                        UserVerificationRequirement.create(selection.path("userVerification").asString("preferred"))),
                AttestationConveyancePreference.NONE,
                null));
        passkeyIds.add(created.getRawId());

        AuthenticatorAttestationResponse response = created.getResponse();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("clientDataJSON", BASE64URL.encodeToString(response.getClientDataJSON()));
        answer.put("attestationObject", BASE64URL.encodeToString(response.getAttestationObject()));
        answer.put("transports", List.of("internal"));
        return credential(created.getRawId(), answer);
    }

    /** What {@code navigator.credentials.get()} answers to options from our API, as JSON. */
    String get(String optionsJson) {
        JsonNode options = JSON.readTree(optionsJson);
        List<PublicKeyCredentialDescriptor> mine = passkeyIds.stream()
                .map(id -> new PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY, id, null))
                .toList();
        PublicKeyCredential<AuthenticatorAssertionResponse, ?> signed = browser.get(new PublicKeyCredentialRequestOptions(
                new DefaultChallenge(bytes(options.get("challenge"))),
                null,
                options.get("rpId").asString(),
                mine,
                UserVerificationRequirement.PREFERRED,
                null));

        AuthenticatorAssertionResponse response = signed.getResponse();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("clientDataJSON", BASE64URL.encodeToString(response.getClientDataJSON()));
        answer.put("authenticatorData", BASE64URL.encodeToString(response.getAuthenticatorData()));
        answer.put("signature", BASE64URL.encodeToString(response.getSignature()));
        if (response.getUserHandle() != null) {
            answer.put("userHandle", BASE64URL.encodeToString(response.getUserHandle()));
        }
        return credential(signed.getRawId(), answer);
    }

    private static String credential(byte[] rawId, Map<String, Object> response) {
        String id = BASE64URL.encodeToString(rawId);
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", id);
        credential.put("rawId", id);
        credential.put("type", "public-key");
        credential.put("response", response);
        credential.put("clientExtensionResults", Map.of());
        credential.put("authenticatorAttachment", "platform");
        return JSON.writeValueAsString(credential);
    }

    private static byte[] bytes(JsonNode base64url) {
        return FROM_BASE64URL.decode(base64url.asString());
    }
}
