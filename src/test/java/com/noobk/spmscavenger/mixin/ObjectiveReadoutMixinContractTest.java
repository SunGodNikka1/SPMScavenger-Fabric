package com.noobk.spmscavenger.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveReadoutMixinContractTest {

    @Test
    void optionalReadoutBridgeIsACommonMixin() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/spmscavenger.mixins.json")) {
            assertNotNull(stream);
            String config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int commonSection = config.indexOf("\"mixins\"");
            int clientSection = config.indexOf("\"client\"");
            int bridge = config.indexOf("\"ObjectiveReadoutMixin\"");
            assertTrue(commonSection >= 0 && bridge > commonSection && bridge < clientSection,
                    "ObjectiveReadout is server-owned and must be bridged from the common mixin list");
            assertFalse(config.substring(clientSection).contains("ObjectiveReadoutMixin"));
        }
    }
}
