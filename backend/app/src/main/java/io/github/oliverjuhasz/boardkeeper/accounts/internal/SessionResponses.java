package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.SessionController.AccountResponse;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.SessionController.SessionResponse;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds the answer to "who is signed in", shared by signing in, setup and the session check. */
@Component
class SessionResponses {

    private final CurrentAccounts currentAccounts;
    private final AccountRepository accounts;
    private final Players players;

    SessionResponses(CurrentAccounts currentAccounts, AccountRepository accounts, Players players) {
        this.currentAccounts = currentAccounts;
        this.accounts = accounts;
        this.players = players;
    }

    @Transactional(readOnly = true)
    SessionResponse of(UUID accountId) {
        return of(currentAccounts.load(accountId).orElseThrow());
    }

    @Transactional(readOnly = true)
    SessionResponse of(CurrentAccount account) {
        Player player = players.find(account.playerId()).orElseThrow();
        Language language = accounts.find(account.accountId()).orElseThrow().language();
        return new SessionResponse(AccountResponse.of(account, player, language));
    }
}
