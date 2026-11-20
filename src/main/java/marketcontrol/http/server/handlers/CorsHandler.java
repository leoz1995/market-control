package marketcontrol.http.server.handlers;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.responses.HttpResponseWriter;

/** Development CORS policy for the separate Angular workspace. This is not authentication. */
public final class CorsHandler implements HttpHandler {
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT");
    private static final Set<String> ALLOWED_HEADERS = Set.of("content-type");

    private final HttpHandler delegate;
    private final String allowedOrigin;

    public CorsHandler(HttpHandler delegate, String allowedOrigin) {
        if (delegate == null) throw new IllegalArgumentException("Handler HTTP obrigatório.");
        if (allowedOrigin == null || allowedOrigin.isBlank() || allowedOrigin.equals("*")) {
            throw new IllegalArgumentException("Configure uma origem CORS explícita.");
        }
        this.delegate = delegate;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !allowedOrigin.equals(origin)) {
            throw new ApiException(403, "ORIGIN_NOT_ALLOWED", "A origem desta requisição não é permitida.");
        }
        if (origin != null) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Request-ID, Location");
            exchange.getResponseHeaders().set("Vary", "Origin");
        }

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            handlePreflight(exchange, origin);
            return;
        }
        delegate.handle(exchange);
    }

    private void handlePreflight(HttpExchange exchange, String origin) throws IOException {
        if (origin == null) {
            throw new ApiException(405, "METHOD_NOT_ALLOWED", "OPTIONS é reservado para preflight CORS.");
        }
        String requestedMethod = exchange.getRequestHeaders().getFirst("Access-Control-Request-Method");
        if (requestedMethod == null || !ALLOWED_METHODS.contains(requestedMethod.toUpperCase(Locale.ROOT))) {
            throw new ApiException(405, "METHOD_NOT_ALLOWED", "Método não permitido para preflight CORS.");
        }
        String requestedHeaders = exchange.getRequestHeaders().getFirst("Access-Control-Request-Headers");
        if (requestedHeaders != null) {
            for (String header : requestedHeaders.split(",")) {
                if (!ALLOWED_HEADERS.contains(header.trim().toLowerCase(Locale.ROOT))) {
                    throw new ApiException(403, "HEADER_NOT_ALLOWED", "Header não permitido para preflight CORS.");
                }
            }
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
        HttpResponseWriter.sendEmpty(exchange, 204);
    }
}
