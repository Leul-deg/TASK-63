package com.reslife.api.domain.crawler;

import com.reslife.api.domain.integration.LocalNetworkValidator;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlFetcherServiceTest {

    @Test
    void blocksRedirectTargetThatLeavesLocalNetwork() throws Exception {
        LocalNetworkValidator validator = mock(LocalNetworkValidator.class);
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> redirect = mock(HttpResponse.class);

        when(redirect.statusCode()).thenReturn(302);
        when(redirect.headers()).thenReturn(HttpHeaders.of(
                Map.of("Location", List.of("http://8.8.8.8/redirect")),
                (name, value) -> true
        ));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(redirect);

        doThrow(new IllegalArgumentException("public address"))
                .when(validator).requireLocalTarget("http://8.8.8.8/redirect");

        CrawlFetcherService service = new CrawlFetcherService(validator, httpClient, "ResLife-Crawler/1.0");

        FetchResult result = service.fetch("http://127.0.0.1/start");

        assertFalse(result.success());
        assertEquals("http://8.8.8.8/redirect", result.url());
        assertTrue(result.errorMessage().contains("Blocked"));
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void followsValidatedLocalRedirectAndReturnsFinalUrl() throws Exception {
        LocalNetworkValidator validator = mock(LocalNetworkValidator.class);
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> redirect = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> ok = mock(HttpResponse.class);

        when(redirect.statusCode()).thenReturn(301);
        when(redirect.headers()).thenReturn(HttpHeaders.of(
                Map.of("Location", List.of("http://127.0.0.1/final")),
                (name, value) -> true
        ));
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn("hello");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirect)
                .thenReturn(ok);

        CrawlFetcherService service = new CrawlFetcherService(validator, httpClient, "ResLife-Crawler/1.0");

        FetchResult result = service.fetch("http://127.0.0.1/start");

        assertTrue(result.success());
        assertEquals("http://127.0.0.1/final", result.url());
        assertEquals(200, result.httpStatus());
        verify(validator).requireLocalTarget("http://127.0.0.1/start");
        verify(validator).requireLocalTarget("http://127.0.0.1/final");
    }
}
