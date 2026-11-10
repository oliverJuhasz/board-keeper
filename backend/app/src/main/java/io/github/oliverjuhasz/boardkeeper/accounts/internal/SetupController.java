package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.accounts.internal.SessionController.SessionResponse;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The first-time setup: creating the first Admin with the link from the log. */
@RestController
@RequestMapping("/api/setup")
@Tag(name = "Setup")
class SetupController {

    private final AccountRepository accounts;
    private final Setup setup;
    private final SignInSessions sessions;
    private final SessionResponses responses;

    SetupController(AccountRepository accounts, Setup setup, SignInSessions sessions, SessionResponses responses) {
        this.accounts = accounts;
        this.setup = setup;
        this.sessions = sessions;
        this.responses = responses;
    }

    @GetMapping
    @Operation(summary = "Whether this installation still needs its first Admin", operationId = "getSetupStatus")
    SetupStatus status() {
        return new SetupStatus(!accounts.anyExist());
    }

    @PostMapping
    @Operation(
            summary = "Create the first Admin and sign in as it",
            description = "Works once, with the token from the link in the server's log.",
            operationId = "setUp")
    SessionResponse setUp(
            @Valid @RequestBody SetupRequest body, HttpServletRequest request, HttpServletResponse response) {
        UUID accountId = setup.createFirstAdmin(body);
        sessions.start(accountId, true, request, response);
        return responses.of(accountId);
    }

    @Schema(name = "SetupStatus", requiredProperties = "required")
    record SetupStatus(boolean required) {}

    record SetupRequest(
            @NotBlank String token,
            @NotNull @Pattern(regexp = AccountRules.USERNAME_PATTERN) String username,
            @NotNull @Size(min = AccountRules.MIN_PASSWORD_LENGTH, max = AccountRules.MAX_PASSWORD_LENGTH)
                    String password,
            @Schema(description = "The name of the Player that the new Account claims") @NotBlank @Size(max = Players.MAX_NAME_LENGTH)
                    String playerName,
            @NotNull Language language) {}

    @Service
    static class Setup {

        private final AccountRepository accounts;
        private final SetupLink link;
        private final Players players;
        private final AccountRegistration registration;

        Setup(AccountRepository accounts, SetupLink link, Players players, AccountRegistration registration) {
            this.accounts = accounts;
            this.link = link;
            this.players = players;
            this.registration = registration;
        }

        @Transactional
        UUID createFirstAdmin(SetupRequest request) {
            if (accounts.anyExist()) {
                throw Problem.conflict("setup.alreadyDone");
            }
            if (!link.claim(request.token())) {
                throw new Problem(HttpStatus.FORBIDDEN, "setup.wrongLink");
            }
            try {
                Player player = players.create(request.playerName());
                return registration.register(
                        request.username(), request.password(), player.id(), request.language(), BuiltInRole.ADMIN);
            } catch (RuntimeException e) {
                link.restore(request.token());
                throw e;
            }
        }
    }
}
