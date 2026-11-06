package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import com.fluxgate.model.BackendServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackendRegistryTest {

    @Test
    @DisplayName("Should initialize backends from configuration properties and sanitize URLs")
    void testInitializationAndSanitization() {
        FluxGateProperties props = new FluxGateProperties();
        props.setBackends(List.of("http://localhost:8081/", "http://localhost:8082///", "http://localhost:8083"));

        BackendRegistry registry = new BackendRegistry(props);
        registry.init();

        assertEquals(3, registry.size());
        assertTrue(registry.findByUrl("http://localhost:8081").isPresent());
        assertTrue(registry.findByUrl("http://localhost:8082").isPresent());
        assertTrue(registry.findByUrl("http://localhost:8083").isPresent());
    }

    @Test
    @DisplayName("Should register and deregister backends dynamically")
    void testDynamicRegistration() {
        FluxGateProperties props = new FluxGateProperties();
        props.setBackends(List.of());

        BackendRegistry registry = new BackendRegistry(props);
        registry.init();
        assertEquals(0, registry.size());

        BackendServer server = registry.registerBackend("http://custom-host:9000/");
        assertEquals("http://custom-host:9000", server.getUrl());
        assertEquals(1, registry.size());

        // Registering duplicate should return existing instance without duplicating
        BackendServer duplicate = registry.registerBackend("http://custom-host:9000");
        assertSame(server, duplicate);
        assertEquals(1, registry.size());

        // Deregister
        assertTrue(registry.deregisterBackend("http://custom-host:9000"));
        assertEquals(0, registry.size());
        assertFalse(registry.deregisterBackend("http://non-existent"));
    }
}
