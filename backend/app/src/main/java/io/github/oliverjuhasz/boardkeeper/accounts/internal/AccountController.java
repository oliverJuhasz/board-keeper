package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** What a signed-in person can change about their own Account. */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Own Account")
class AccountController {

    private final AccountRepository accounts;

    AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @PutMapping("/language")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @Operation(summary = "Choose the language of the interface", operationId = "setOwnLanguage")
    void setLanguage(@AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody LanguageRequest body) {
        accounts.setLanguage(account.accountId(), body.language());
    }

    record LanguageRequest(@NotNull Language language) {}
}
