package com.reslife.api.domain.integration;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Buffers the HTTP request body into a byte array so it can be read more than
 * once — first in {@link IntegrationAuthFilter} for HMAC verification, and again
 * by the Spring MVC controller.
 */
class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream stream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override public boolean isFinished()                       { return stream.available() == 0; }
            @Override public boolean isReady()                         { return true; }
            @Override public void    setReadListener(ReadListener rl)  {}
            @Override public int     read()                            { return stream.read(); }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    byte[] getCachedBody() {
        return cachedBody;
    }
}
