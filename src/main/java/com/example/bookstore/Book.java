package com.example.bookstore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * A book in the catalog. Identified by its ISBN.
 */
public record Book(
        @NotBlank String isbn,
        @NotBlank String title,
        @NotBlank String author,
        @NotNull @PositiveOrZero BigDecimal price) {
}
