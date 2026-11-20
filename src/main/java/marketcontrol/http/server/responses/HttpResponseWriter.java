
package marketcontrol.http.server.responses;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;

public final class HttpResponseWriter {

	private HttpResponseWriter() {
	}

	public static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {

		byte[] response = JsonSupport.MAPPER.writeValueAsBytes(body);

		send(exchange, status, "application/json; charset=UTF-8", response);
	}

	public static void sendText(HttpExchange exchange, int status, String body) throws IOException {

		byte[] response = body.getBytes(StandardCharsets.UTF_8);

		send(exchange, status, "text/plain; charset=UTF-8", response);
	}

	public static void sendEmpty(HttpExchange exchange, int status) throws IOException {
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(status, -1);
		exchange.close();
	}

	private static void send(HttpExchange exchange, int status, String contentType, byte[] response)
			throws IOException {

		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");

		exchange.sendResponseHeaders(status, response.length);

		try (OutputStream os = exchange.getResponseBody()) {
			os.write(response);
		}
	}
}
