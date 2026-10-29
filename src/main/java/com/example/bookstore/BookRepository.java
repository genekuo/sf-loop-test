package com.example.bookstore;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory book catalog backed by a {@link ConcurrentHashMap} (no database).
 */
@Repository
public class BookRepository {

    private final ConcurrentMap<String, Book> books = new ConcurrentHashMap<>();

    public Collection<Book> findAll() {
        return books.values();
    }

    public Optional<Book> findByIsbn(String isbn) {
        return Optional.ofNullable(books.get(isbn));
    }

    public boolean exists(String isbn) {
        return books.containsKey(isbn);
    }

    /**
     * Insert a book only if its ISBN is not already present.
     *
     * @return {@code true} if stored, {@code false} if the ISBN already existed.
     */
    public boolean insert(Book book) {
        return books.putIfAbsent(book.isbn(), book) == null;
    }

    /**
     * Upsert a book, returning whether a new entry was created.
     *
     * @return {@code true} if a new book was created, {@code false} if an existing one was replaced.
     */
    public boolean save(Book book) {
        return books.put(book.isbn(), book) == null;
    }

    /**
     * Remove a book by ISBN.
     *
     * @return {@code true} if a book was removed.
     */
    public boolean delete(String isbn) {
        return books.remove(isbn) != null;
    }
}
