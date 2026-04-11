package com.reslife.api.domain.integration;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates that a webhook target URL resolves to a private/local IP address,
 * preventing outgoing requests from being routed to the public internet (SSRF).
 *
 * <h3>Accepted address ranges</h3>
 * <ul>
 *   <li>Loopback — 127.0.0.0/8, ::1</li>
 *   <li>Private class A — 10.0.0.0/8</li>
 *   <li>Private class B — 172.16.0.0/12</li>
 *   <li>Private class C — 192.168.0.0/16</li>
 *   <li>Link-local — 169.254.0.0/16, fe80::/10</li>
 * </ul>
 */
@Component
public class LocalNetworkValidator {

    /**
     * @throws IllegalArgumentException if the URL cannot be parsed, has no host,
     *                                  or resolves to a non-private address
     */
    public void requireLocalTarget(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook target URL must not be blank");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed webhook URL: " + url);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Webhook URL must use http or https, got: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL has no host: " + url);
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve webhook host '" + host + "': " + e.getMessage());
        }
        if (!isPrivate(address)) {
            throw new IllegalArgumentException(
                    "Webhook target must resolve to a private/local network address. " +
                    "'" + host + "' resolved to " + address.getHostAddress() +
                    " which is not in a private range.");
        }
    }

    private boolean isPrivate(InetAddress addr) {
        return addr.isLoopbackAddress()
            || addr.isSiteLocalAddress()
            || addr.isLinkLocalAddress();
    }
}
