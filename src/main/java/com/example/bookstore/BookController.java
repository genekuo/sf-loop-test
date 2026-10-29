package com.example.bookstore;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Reactive REST API for the book catalog (Spring WebFlux).
 */
@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<Book> list() {
        return Flux.fromIterable(service.findAll());
    }

    @GetMapping("/{isbn}")
    public Mono<Book> get(@PathVariable String isbn) {
        return Mono.fromCallable(() -> service.findByIsbn(isbn));
    }

    @PostMapping
    public Mono<ResponseEntity<Book>> create(@Valid @RequestBody Book book, UriComponentsBuilder uriBuilder) {
        return Mono.fromCallable(() -> {
            Book created = service.create(book);
            URI location = uriBuilder.path("/books/{isbn}")
                    .buildAndExpand(created.isbn())
                    .toUri();
            return ResponseEntity.created(location).body(created);
        });
    }

    @PutMapping("/{isbn}")
    public Mono<ResponseEntity<Book>> upsert(@PathVariable String isbn, @Valid @RequestBody Book book) {
        return Mono.fromCallable(() -> {
            BookService.UpsertResult result = service.upsert(isbn, book);
            HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(result.book());
        });
    }

    @DeleteMapping("/{isbn}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String isbn) {
        return Mono.fromCallable(() -> {
            service.delete(isbn);
            return ResponseEntity.noContent().<Void>build();
        });
    }
}
