package com.example.bookstore;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookRepositoryTest {

    private final BookRepository repository = new BookRepository();

    private static Book book(String isbn, String title) {
        return new Book(isbn, title, "Author", new BigDecimal("9.99"));
    }

    @Test
    void insertStoresNewBookAndRejectsDuplicates() {
        assertThat(repository.insert(book("isbn-1", "First"))).isTrue();
        assertThat(repository.insert(book("isbn-1", "Again"))).isFalse();
        assertThat(repository.findByIsbn("isbn-1")).map(Book::title).contains("First");
    }

    @Test
    void saveReportsWhetherEntryWasCreated() {
        assertThat(repository.save(book("isbn-2", "New"))).isTrue();
        assertThat(repository.save(book("isbn-2", "Updated"))).isFalse();
        assertThat(repository.findByIsbn("isbn-2")).map(Book::title).contains("Updated");
    }

    @Test
    void deleteRemovesBook() {
        repository.insert(book("isbn-3", "Doomed"));
        assertThat(repository.delete("isbn-3")).isTrue();
        assertThat(repository.delete("isbn-3")).isFalse();
        assertThat(repository.findByIsbn("isbn-3")).isEmpty();
    }
}
