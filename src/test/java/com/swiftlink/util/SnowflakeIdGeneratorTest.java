package com.swiftlink.util;

import com.swiftlink.config.SwiftLinkProperties;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateUniquePositiveIds() {
        SwiftLinkProperties properties = new SwiftLinkProperties();
        properties.getSnowflake().setWorkerId(1);
        properties.getSnowflake().setDatacenterId(1);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(properties);

        int total = 10000;
        Set<Long> ids = new HashSet<>(total);
        for (int i = 0; i < total; i++) {
            long id = generator.nextId();
            assertTrue(id > 0);
            assertTrue(ids.add(id), "出现重复 ID");
        }
        assertEquals(total, ids.size());
    }

    @Test
    void shouldRejectIllegalWorkerId() {
        SwiftLinkProperties properties = new SwiftLinkProperties();
        properties.getSnowflake().setWorkerId(32);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(properties));
    }
}
