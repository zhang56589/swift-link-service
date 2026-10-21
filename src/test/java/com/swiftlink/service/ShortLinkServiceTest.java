package com.swiftlink.service;

import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.dto.CreateLinkRequest;
import com.swiftlink.dto.CreateLinkResponse;
import com.swiftlink.entity.ShortLink;
import com.swiftlink.exception.BusinessException;
import com.swiftlink.mapper.ShortLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    @Mock
    private ShortLinkMapper shortLinkMapper;

    @Mock
    private ShortCodeService shortCodeService;

    @Mock
    private LinkCacheService linkCacheService;

    @Mock
    private BlacklistService blacklistService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private ShortLinkService shortLinkService;

    @BeforeEach
    void setUp() {
        SwiftLinkProperties properties = new SwiftLinkProperties();
        shortLinkService = new ShortLinkService(shortLinkMapper, shortCodeService, linkCacheService,
                blacklistService, properties, redissonClient);
    }

    @Test
    void shouldCreateShortLink() throws Exception {
        when(shortCodeService.acquire()).thenReturn("abc123");
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(shortLinkMapper.selectCount(any())).thenReturn(0L);
        when(shortLinkMapper.insert(any(ShortLink.class))).thenAnswer(invocation -> {
            ShortLink link = invocation.getArgument(0);
            link.setId(1L);
            return 1;
        });

        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginUrl("https://example.com/path?a=1");

        CreateLinkResponse response = shortLinkService.create(request);

        assertEquals("abc123", response.getShortCode());
        assertEquals("http://s.link/abc123", response.getShortUrl());
        verify(shortLinkMapper).insert(any(ShortLink.class));
    }

    @Test
    void shouldRejectInvalidUrl() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginUrl("javascript:alert(1)");

        assertThrows(BusinessException.class, () -> shortLinkService.create(request));
        verifyNoInteractions(shortCodeService);
    }
}
