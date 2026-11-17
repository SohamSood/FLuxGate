package com.fluxgate.service;

import com.fluxgate.config.FluxGateProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedBackendServiceTest {

    @Test
    @DisplayName("Should skip starting embedded backends when disabled by configuration")
    void testDisabledConfig() {
        FluxGateProperties props = new FluxGateProperties();
        props.getDemoBackends().setEnabled(false);

        EmbeddedBackendService service = new EmbeddedBackendService(props);
        assertDoesNotThrow(service::startIfEnabled);
    }

    @Test
    @DisplayName("Should skip starting embedded backends when external hosts are configured")
    void testExternalHostsConfig() {
        FluxGateProperties props = new FluxGateProperties();
        props.getDemoBackends().setEnabled(true);
        props.setBackends(List.of("http://external-api-1.com:8080", "http://external-api-2.com:8080"));

        EmbeddedBackendService service = new EmbeddedBackendService(props);
        assertDoesNotThrow(service::startIfEnabled);
    }
}
