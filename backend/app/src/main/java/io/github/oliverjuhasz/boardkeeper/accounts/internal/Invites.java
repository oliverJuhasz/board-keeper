package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.InviteRepository.InviteRow;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invites: offers to the person behind a Player to register an Account that claims that
 * Player. An Invite is a link that works once and for a limited time.
 */
@Service
class Invites {

    static final Duration VALIDITY = Duration.ofDays(7);

    private static final String NOT_VALID = "invites.notValid";

    private final InviteRepository invites;
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final Players players;
    private final AccountRegistration registration;

    Invites(
            InviteRepository invites,
            AccountRepository accounts,
            RoleRepository roles,
            Players players,
            AccountRegistration registration) {
        this.invites = invites;
        this.accounts = accounts;
        this.roles = roles;
        this.players = players;
        this.registration = registration;
    }

    List<InviteRow> all() {
        return invites.findAll();
    }

    /**
     * Invites a Player, replacing any earlier Invite for the same Player. Without a Role, the
     * new Account will be a Member. Any other Role needs the Permission to manage Accounts as
     * well: otherwise inviting would be a way around it.
     */
    @Transactional
    NewInvite create(UUID playerId, UUID roleId, CurrentAccount creator) {
        if (players.find(playerId).isEmpty()) {
            throw Problem.notFound("players.notFound");
        }
        if (accounts.existsByPlayer(playerId)) {
            throw Problem.conflict("invites.playerHasAccount");
        }
        UUID member = roles.idOf(BuiltInRole.MEMBER);
        UUID role = roleId == null ? member : roleId;
        if (!role.equals(member)) {
            if (!creator.has(Permission.MANAGE_ACCOUNTS)) {
                throw new Problem(HttpStatus.FORBIDDEN, "invites.roleNotAllowed");
            }
            if (!roles.exists(role)) {
                throw Problem.notFound("roles.notFound");
            }
        }

        String token = LinkTokens.newToken();
        invites.deleteByPlayer(playerId);
        InviteRow invite = invites.insert(
                LinkTokens.hash(token), playerId, role, creator.accountId(), OffsetDateTime.now().plus(VALIDITY));
        return new NewInvite(invite, token);
    }

    @Transactional
    void cancel(UUID inviteId) {
        if (!invites.delete(inviteId)) {
            throw Problem.notFound("invites.notFound");
        }
    }

    /** The Player an Invite's link is for. Anything wrong with the link gives the same Problem. */
    @Transactional(readOnly = true)
    OpenInvite open(String token) {
        InviteRow invite = valid(token);
        Player player = players.find(invite.playerId()).orElseThrow(() -> Problem.notFound(NOT_VALID));
        return new OpenInvite(player, invite.expiresAt());
    }

    /** Registers the Account the Invite offers, and uses the Invite up. */
    @Transactional
    UUID accept(String token, String username, String password, Language language) {
        InviteRow invite = valid(token);
        UUID accountId = registration.register(username, password, invite.playerId(), language, invite.roleId());
        invites.delete(invite.id());
        return accountId;
    }

    private InviteRow valid(String token) {
        return invites.findByTokenHash(LinkTokens.hash(token))
                .filter(invite -> !invite.isExpiredAt(OffsetDateTime.now()))
                .orElseThrow(() -> Problem.notFound(NOT_VALID));
    }

    /** A just-created Invite with its token, which is not stored and cannot be shown again. */
    record NewInvite(InviteRow invite, String token) {}

    record OpenInvite(Player player, OffsetDateTime expiresAt) {}
}
