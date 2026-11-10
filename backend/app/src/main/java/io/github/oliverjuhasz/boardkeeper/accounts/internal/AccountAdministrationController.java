package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.AccountRepository.AccountSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Managing other people's Accounts. Everything here needs the Permission to manage Accounts. */
@RestController
@RequestMapping("/api/accounts")
@RequiresPermission(Permission.MANAGE_ACCOUNTS)
@Tag(name = "Accounts")
class AccountAdministrationController {

    private final AccountAdministration administration;

    AccountAdministrationController(AccountAdministration administration) {
        this.administration = administration;
    }

    @GetMapping
    @Operation(summary = "List the Accounts by username", operationId = "listAccounts")
    List<AccountSummaryResponse> list() {
        return administration.all().stream().map(AccountSummaryResponse::from).toList();
    }

    @PutMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Set which Roles an Account holds",
            description = "Only Roles within the caller's own Permissions can be given or taken away, "
                    + "and the last Admin keeps the Admin Role.",
            operationId = "setAccountRoles")
    void setRoles(
            @PathVariable UUID id, @AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody RolesRequest body) {
        administration.setRoles(id, body.roleIds(), account);
    }

    @PutMapping("/{id}/disabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Disable or enable an Account",
            description = "A disabled Account cannot sign in and is signed out everywhere. "
                    + "Its Player and history stay.",
            operationId = "setAccountDisabled")
    void setDisabled(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentAccount account,
            @Valid @RequestBody DisabledRequest body,
            HttpServletRequest request) {
        administration.setDisabled(id, body.disabled(), account, request);
    }

    record RolesRequest(@NotNull Set<UUID> roleIds) {}

    record DisabledRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean disabled) {}

    @Schema(
            name = "Account",
            requiredProperties = {"id", "username", "playerId", "roleIds", "disabled"})
    record AccountSummaryResponse(UUID id, String username, UUID playerId, List<UUID> roleIds, boolean disabled) {

        static AccountSummaryResponse from(AccountSummary account) {
            return new AccountSummaryResponse(
                    account.id(), account.username(), account.playerId(), account.roleIds(), account.disabled());
        }
    }
}
