package io.casehub.devtown.app.spi;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredEndpointResolverTest {

    @Test
    void emptyStringReturnsEmpty() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = "";
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void nullReturnsEmpty() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = null;
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void blankReturnsEmpty() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = "   ";
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void validUrlReturnsUri() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = "https://abc-123.trycloudflare.com";
        var result = resolver.resolve();
        assertTrue(result.isPresent());
        assertEquals(URI.create("https://abc-123.trycloudflare.com"), result.get());
    }

    @Test
    void trailingSlashPreserved() {
        var resolver = new ConfiguredEndpointResolver();
        resolver.publicUrl = "https://devtown.example.com/";
        var result = resolver.resolve();
        assertTrue(result.isPresent());
        assertEquals(URI.create("https://devtown.example.com/"), result.get());
    }
}
