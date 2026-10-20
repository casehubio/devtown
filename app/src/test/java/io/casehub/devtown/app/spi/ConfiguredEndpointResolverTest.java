package io.casehub.devtown.app.spi;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredEndpointResolverTest {

    @Test
    void emptyStringReturnsEmpty() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = Optional.of("");
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void missingReturnsEmpty() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = Optional.empty();
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void blankReturnsEmpty() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = Optional.of("   ");
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void validUrlReturnsUri() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = Optional.of("https://abc-123.trycloudflare.com");
        var result = resolver.resolve();
        assertTrue(result.isPresent());
        assertEquals(URI.create("https://abc-123.trycloudflare.com"), result.get());
    }

    @Test
    void trailingSlashPreserved() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = Optional.of("https://devtown.example.com/");
        var result = resolver.resolve();
        assertTrue(result.isPresent());
        assertEquals(URI.create("https://devtown.example.com/"), result.get());
    }
}
