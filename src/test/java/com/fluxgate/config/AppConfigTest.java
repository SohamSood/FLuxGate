package com.fluxgate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    @DisplayName("Should create HttpClient bean with configured properties")
    void testHttpClientBeanCreation() {
        AppConfig config = new AppConfig();
        FluxGateProperties props = new FluxGateProperties();
        props.getProxy().setConnectTimeoutMs(4000);

        HttpClient client = config.httpClient(props);
        assertNotNull(client);
        assertTrue(client.connectTimeout().isPresent());
        assertEquals(4000, client.connectTimeout().get().toMillis());
    }
}
