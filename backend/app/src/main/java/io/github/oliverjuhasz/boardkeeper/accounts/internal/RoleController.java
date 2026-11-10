package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.RoleRepository.RoleRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles")
class RoleController {

    private final RoleRepository roles;

    RoleController(RoleRepository roles) {
        this.roles = roles;
    }

    @GetMapping
    @Operation(
            summary = "List the Roles with their Permissions",
            description = "The built-in Roles come first, then the others by name.",
            operationId = "listRoles")
    List<RoleResponse> list() {
        return roles.findAll().stream().map(RoleResponse::from).toList();
    }

    @Schema(
            name = "Role",
            requiredProperties = {"id", "name", "permissions"})
    record RoleResponse(
            UUID id,
            String name,
            @Schema(description = "ADMIN or MEMBER for the two built-in Roles; absent for any other") BuiltInRole builtIn,
            Set<Permission> permissions) {

        static RoleResponse from(RoleRow role) {
            return new RoleResponse(role.id(), role.name(), role.builtIn(), new TreeSet<>(role.permissions()));
        }
    }
}
