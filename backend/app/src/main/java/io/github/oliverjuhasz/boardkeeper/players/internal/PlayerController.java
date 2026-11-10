package io.github.oliverjuhasz.boardkeeper.players.internal;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Players")
class PlayerController {

    private final Players players;

    PlayerController(Players players) {
        this.players = players;
    }

    @GetMapping
    @Operation(summary = "List the Players by name", operationId = "listPlayers")
    List<PlayerResponse> list() {
        return players.all().stream().map(PlayerResponse::from).toList();
    }

    @PostMapping
    @RequiresPermission(Permission.CREATE_PLAYERS)
    @Operation(summary = "Create a Player", operationId = "createPlayer")
    PlayerResponse create(@Valid @RequestBody PlayerRequest body) {
        return PlayerResponse.from(players.create(body.name()));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.EDIT_PLAYERS)
    @Operation(summary = "Rename a Player", operationId = "renamePlayer")
    PlayerResponse rename(@PathVariable UUID id, @Valid @RequestBody PlayerRequest body) {
        return PlayerResponse.from(players.rename(id, body.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.REMOVE_PLAYERS)
    @Operation(
            summary = "Remove a Player",
            description = "Only a Player without an Account and without Plays can be removed.",
            operationId = "removePlayer")
    void remove(@PathVariable UUID id) {
        players.remove(id);
    }

    record PlayerRequest(@NotBlank @Size(max = Players.MAX_NAME_LENGTH) String name) {}

    @Schema(name = "Player", requiredProperties = {"id", "name"})
    record PlayerResponse(UUID id, String name) {

        static PlayerResponse from(Player player) {
            return new PlayerResponse(player.id(), player.name());
        }
    }
}
