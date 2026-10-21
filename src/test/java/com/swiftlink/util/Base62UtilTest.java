package com.swiftlink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62UtilTest {

    @Test
    void shouldEncodeAndDecodeRoundTrip() {
        long[] values = {0L, 1L, 61L, 62L, 3843L, 123456789L, 2_600_000_000_000_000_000L};
        for (long value : values) {
            String code = Base62Util.encode(value);
            assertEquals(value, Base62Util.decode(code));
        }
    }

    @Test
    void shouldKeepCodeShort() {
        String code = Base62Util.encode(2_600_000_000_000_000_000L);
        assertTrue(code.length() <= 11);
    }

    @Test
    void shouldRejectNegativeAndIllegalCharacter() {
        assertThrows(IllegalArgumentException.class, () -> Base62Util.encode(-1L));
        assertThrows(IllegalArgumentException.class, () -> Base62Util.decode("abc-"));
    }
}
