package io.github.oliverjuhasz.boardkeeper.exports.internal;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import")
@Tag(name = "Export and import")
class ImportController {

    private final Imports imports;

    ImportController(Imports imports) {
        this.imports = imports;
    }

    @PostMapping("/review")
    @RequiresPermission(Permission.IMPORT)
    @Operation(
            summary = "Review an Import",
            description = "What importing an Export would do, without changing anything: where the Plays of each of"
                    + " its Games would go, which Player here has the name of each of its Players, how many of its"
                    + " Plays are here already, and which ones this Installation can't take, and why. An Export this"
                    + " Board Keeper can't read is answered with imports.notAnExport, imports.formatTooNew or"
                    + " imports.fileBroken. For Admins only.",
            operationId = "reviewImport")
    Import.Review review(@RequestBody Export file) {
        return imports.review(file);
    }

    @PostMapping
    @RequiresPermission(Permission.IMPORT)
    @Operation(
            summary = "Import an Export",
            description = "Adds the Plays of an Export that aren't here yet, as they were where they were recorded:"
                    + " with their ids, their Results, and who recorded them and when. Plays that are here already"
                    + " are left as they are, and Plays this Installation can't take are listed. Each of the file's"
                    + " Players becomes the Player here that was chosen for them, or a new one; each of its Games"
                    + " becomes the Game it was reviewed to match, or a new one. Either all of it happens, or, when"
                    + " something fails, nothing. For Admins only.",
            operationId = "importExport")
    Import.Result importFile(@Valid @RequestBody Import.Request body) {
        return imports.importFile(body.file(), body.players());
    }
}
