package marketcontrol.http.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;

import marketcontrol.http.server.responses.JsonSupport;

public final class HttpRequests {
    private HttpRequests() {}

    public static String[] segments(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith("/api")) return new String[0];
        String remainder = path.substring(4);
        if (remainder.isEmpty() || remainder.equals("/")) return new String[0];
        return Arrays.stream(remainder.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
    }

    public static void requireMethod(HttpExchange exchange, String... allowed) {
        String actual = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (Arrays.stream(allowed).noneMatch(actual::equals)) {
            exchange.getResponseHeaders().set("Allow", String.join(", ", allowed));
            throw new ApiException(405, "METHOD_NOT_ALLOWED", "Método HTTP não permitido para esta rota.");
        }
    }

    public static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "INVALID_ID", "O identificador informado não é um UUID válido.");
        }
    }

    public static <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !isJson(contentType)) {
            throw new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "Envie o corpo com Content-Type application/json.");
        }
        long declaredLength = exchange.getRequestHeaders().getFirst("Content-Length") == null
                ? -1 : parseLength(exchange.getRequestHeaders().getFirst("Content-Length"));
        if (declaredLength > JsonSupport.MAX_REQUEST_BYTES) {
            throw new ApiException(413, "PAYLOAD_TOO_LARGE", "O corpo da requisição excede o limite permitido.");
        }

        byte[] body;
        try (InputStream input = exchange.getRequestBody()) {
            body = input.readNBytes(JsonSupport.MAX_REQUEST_BYTES + 1);
        }
        if (body.length > JsonSupport.MAX_REQUEST_BYTES) {
            throw new ApiException(413, "PAYLOAD_TOO_LARGE", "O corpo da requisição excede o limite permitido.");
        }
        try {
            return JsonSupport.MAPPER.readValue(body, type);
        } catch (JsonProcessingException ex) {
            throw new ApiException(400, "INVALID_JSON", "O corpo da requisição não contém JSON válido.");
        }
    }

    private static boolean isJson(String contentType) {
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }

    private static long parseLength(String value) {
        try {
            long length = Long.parseLong(value);
            if (length < 0) throw new NumberFormatException();
            return length;
        } catch (NumberFormatException ex) {
            throw new ApiException(400, "INVALID_CONTENT_LENGTH", "O Content-Length informado é inválido.");
        }
    }
}
