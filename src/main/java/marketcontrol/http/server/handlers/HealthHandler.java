package marketcontrol.http.server.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.http.server.responses.HttpResponseWriter;

public class HealthHandler implements HttpHandler {

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		HttpResponseWriter.sendJson(exchange, 200, new HealthResponse("UP"));
	}

	private record HealthResponse(String status) {}

}
