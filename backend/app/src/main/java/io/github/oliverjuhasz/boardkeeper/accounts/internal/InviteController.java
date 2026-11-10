package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.InviteRepository.InviteRow;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.Invites.NewInvite;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creating and cancelling Invites. Everything here needs the Permission to manage Invites. */
@RestController
@RequestMapping("/api/invites")
@RequiresPermission(Permission.MANAGE_INVITES)
@Tag(name = "Invites")
class InviteController {

    private final Invites invites;

    InviteController(Invites invites) {
        this.invites = invites;
    }

    @GetMapping
    @Operation(summary = "List the open Invites", operationId = "listInvites")
    List<InviteResponse> list() {
        return invites.all().stream().map(InviteResponse::from).toList();
    }

    @PostMapping
    @Operation(
            summary = "Invite a Player to register an Account",
            description = "Replaces any earlier Invite for the same Player. "
                    + "The answer holds the link's token, which cannot be asked for again.",
            operationId = "createInvite")
    NewInviteResponse create(@AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody InviteRequest body) {
        return NewInviteResponse.from(invites.create(body.playerId(), body.roleId(), account));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel an Invite", operationId = "cancelInvite")
    void cancel(@PathVariable UUID id) {
        invites.cancel(id);
    }

    record InviteRequest(
            @NotNull UUID playerId,
            @Schema(description = "The Role the new Account starts with. Member if left out.") UUID roleId) {}

    @Schema(
            name = "Invite",
            requiredProperties = {"id", "playerId", "roleId", "expiresAt", "expired"})
    record InviteResponse(UUID id, UUID playerId, UUID roleId, OffsetDateTime expiresAt, boolean expired) {

        static InviteResponse from(InviteRow invite) {
            return new InviteResponse(
                    invite.id(),
                    invite.playerId(),
                    invite.roleId(),
                    invite.expiresAt(),
                    invite.isExpiredAt(OffsetDateTime.now()));
        }
    }

    @Schema(
            name = "NewInvite",
            requiredProperties = {"invite", "token"})
    record NewInviteResponse(
            InviteResponse invite,
            @Schema(description = "The secret part of the link: the person registers at /register/{token}") String token) {

        static NewInviteResponse from(NewInvite created) {
            return new NewInviteResponse(InviteResponse.from(created.invite()), created.token());
        }
    }
}
