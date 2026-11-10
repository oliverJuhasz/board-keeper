package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
@Tag(name = "Claims")
class ClaimController {

    private final AccountRepository accounts;

    ClaimController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    @Operation(summary = "Which Players have an Account", operationId = "listClaims")
    ClaimsResponse list() {
        return new ClaimsResponse(accounts.findClaimedPlayerIds());
    }

    @Schema(name = "Claims", requiredProperties = "playerIds")
    record ClaimsResponse(@Schema(description = "The Players that an Account has claimed") List<UUID> playerIds) {}
}
