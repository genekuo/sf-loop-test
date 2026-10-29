package com.example.bookstore;

/**
 * Raised when a book cannot be found for a given ISBN.
 */
public class BookNotFoundException extends RuntimeException {

    private final String isbn;

    public BookNotFoundException(String isbn) {
        super("No book found with ISBN '" + isbn + "'");
        this.isbn = isbn;
    }

    public String getIsbn() {
        return isbn;
    }
}
