package io.github.oliverjuhasz.boardkeeper.problems.internal;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the shape of an error answer to the API's description, as the "default" response of
 * every operation. The frontend's generated types then know what an error looks like.
 */
@Configuration(proxyBeanMethods = false)
class ProblemApiDocumentation {

    @Bean
    GlobalOpenApiCustomizer problemResponses() {
        return openApi -> {
            ModelConverters.getInstance().readAll(ProblemResponse.class).forEach(openApi.getComponents()::addSchemas);
            var schema = new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/Problem");
            var response = new ApiResponse()
                    .description("The request could not be carried out")
                    .content(new Content().addMediaType("application/problem+json", new MediaType().schema(schema)));
            openApi.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .forEach(operation -> operation.getResponses().addApiResponse("default", response));
        };
    }

    /** Only describes the JSON that {@link ProblemHandler} sends; nothing creates instances of it. */
    @Schema(name = "Problem", requiredProperties = {"status"})
    record ProblemResponse(
            @Schema(description = "The HTTP status") int status,
            @Schema(description = "What went wrong, as a code for the frontend to translate, such as players.nameTaken")
                    String code,
            @Schema(
                            description =
                                    "For the code plays.invalidEntry: why the Game Module refused the Play, as a name in that"
                                            + " module's texts, such as generic.invalid.winnerMissing")
                    String reason,
            @Schema(description = "For the code request.invalid: the fields that broke a rule") List<FieldProblemResponse> errors,
            String title,
            String detail) {}

    @Schema(name = "FieldProblem", requiredProperties = {"field", "code"})
    record FieldProblemResponse(
            String field, @Schema(description = "The rule that was broken, such as size or notblank") String code) {}
}
