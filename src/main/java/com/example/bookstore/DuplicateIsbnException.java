package com.example.bookstore;

/**
 * Raised when a book is created with an ISBN that already exists in the catalog.
 */
public class DuplicateIsbnException extends RuntimeException {

    private final String isbn;

    public DuplicateIsbnException(String isbn) {
        super("A book with ISBN '" + isbn + "' already exists");
        this.isbn = isbn;
    }

    public String getIsbn() {
        return isbn;
    }
}
