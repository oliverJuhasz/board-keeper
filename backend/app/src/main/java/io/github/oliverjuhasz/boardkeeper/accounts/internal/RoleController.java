package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.RoleRepository.RoleRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
@RequestMapping("/api/roles")
@Tag(name = "Roles")
class RoleController {

    private final RoleRepository roles;
    private final RoleAdministration administration;

    RoleController(RoleRepository roles, RoleAdministration administration) {
        this.roles = roles;
        this.administration = administration;
    }

    @GetMapping
    @Operation(
            summary = "List the Roles with their Permissions",
            description = "The built-in Roles come first, then the others by name.",
            operationId = "listRoles")
    List<RoleResponse> list() {
        return roles.findAll().stream().map(RoleResponse::from).toList();
    }

    @PostMapping
    @RequiresPermission(Permission.MANAGE_ROLES)
    @Operation(
            summary = "Create a Role",
            description = "It cannot hold the Permissions that are for Admins only.",
            operationId = "createRole")
    RoleResponse create(@Valid @RequestBody RoleRequest body) {
        return RoleResponse.from(administration.create(body.name(), body.permissions()));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.MANAGE_ROLES)
    @Operation(
            summary = "Change a Role's name and Permissions",
            description = "The Admin Role cannot be changed. The Member Role keeps its name.",
            operationId = "updateRole")
    RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest body) {
        return RoleResponse.from(administration.update(id, body.name(), body.permissions()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.MANAGE_ROLES)
    @Operation(
            summary = "Delete a Role",
            description = "Accounts that held it lose it. The two built-in Roles cannot be deleted.",
            operationId = "deleteRole")
    void delete(@PathVariable UUID id) {
        administration.delete(id);
    }

    record RoleRequest(
            @NotBlank @Size(max = RoleAdministration.MAX_NAME_LENGTH) String name, @NotNull Set<Permission> permissions) {}

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
