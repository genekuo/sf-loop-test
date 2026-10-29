package com.example.bookstore;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Catalog operations over the {@link BookRepository}.
 */
@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public List<Book> findAll() {
        return List.copyOf(repository.findAll());
    }

    public Book findByIsbn(String isbn) {
        return repository.findByIsbn(isbn)
                .orElseThrow(() -> new BookNotFoundException(isbn));
    }

    /**
     * Create a new book. Fails if the ISBN already exists.
     */
    public Book create(Book book) {
        if (!repository.insert(book)) {
            throw new DuplicateIsbnException(book.isbn());
        }
        return book;
    }

    /**
     * Upsert a book under the given ISBN.
     *
     * @return the upsert result, carrying whether the book was newly created.
     */
    public UpsertResult upsert(String isbn, Book book) {
        Book toStore = new Book(isbn, book.title(), book.author(), book.price());
        boolean created = repository.save(toStore);
        return new UpsertResult(toStore, created);
    }

    /**
     * Delete a book. Fails if the ISBN does not exist.
     */
    public void delete(String isbn) {
        if (!repository.delete(isbn)) {
            throw new BookNotFoundException(isbn);
        }
    }

    /**
     * Outcome of an upsert: the stored book and whether it was newly created.
     */
    public record UpsertResult(Book book, boolean created) {
    }
}
