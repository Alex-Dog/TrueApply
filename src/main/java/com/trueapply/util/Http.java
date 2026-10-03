package com.trueapply.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Shared HTTP client for the public job APIs. */
public final class Http {
    public static final String USER_AGENT = "TrueApply/0.1 (+https://github.com/)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Http() {
    }

    public static HttpClient client() {
        return CLIENT;
    }

    public static JsonNode getJson(String url) throws IOException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("GET " + url + " returned HTTP " + response.statusCode());
        }
        return Json.MAPPER.readTree(response.body());
    }

    public static HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
