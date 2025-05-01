package dev.simplecore.simplix.web.controller;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Successfully processed"
    ),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = SimpliXApiResponse.class),
            examples = @ExampleObject(
                value = "{" +
                    "\"type\":\"FAILURE\"," +
                    "\"message\":\"Invalid request parameters\"," +
                    "\"body\":null," +
                    "\"error\":\"name field is required\"," +
                    "\"timestamp\":\"2024-02-06T10:30:00\"" +
                    "}"
            )
        )
    ),
    @ApiResponse(
        responseCode = "500",
        description = "Server error",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = SimpliXApiResponse.class),
            examples = @ExampleObject(
                value = "{" +
                    "\"type\":\"ERROR\"," +
                    "\"message\":\"Internal server error\"," +
                    "\"body\":null," +
                    "\"error\":\"Unexpected error occurred\"," +
                    "\"timestamp\":\"2024-02-06T10:30:00\"" +
                    "}"
            )
        )
    )
})
public @interface SimpliXStandardApi {
}