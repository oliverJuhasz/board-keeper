package io.github.oliverjuhasz.boardkeeper.exports.internal;

import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.access.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/export")
@Tag(name = "Export and import")
class ExportController {

    private final Exports exports;

    ExportController(Exports exports) {
        this.exports = exports;
    }

    @GetMapping
    @RequiresPermission(Permission.EXPORT)
    @Operation(
            summary = "Make an Export",
            description = "The Installation's Games, Players and Plays, to use them elsewhere or to import them into"
                    + " another Installation. Nothing about Accounts. The answer is meant to be saved as a file, and"
                    + " suggests the name board-keeper-<date>.json. For Admins only.",
            operationId = "getExport")
    ResponseEntity<Export> export() {
        Export export = exports.export();
        String fileName = "board-keeper-" + LocalDate.ofInstant(export.exportedAt(), ZoneOffset.UTC) + ".json";
        // "attachment" tells a browser to save the answer instead of showing it.
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(export);
    }
}
