package marketcontrol.http.server.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.dtos.auth.LoginRequest;
import marketcontrol.dtos.auth.RefreshRequest;
import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.HttpRequests;
import marketcontrol.http.server.responses.HttpResponseWriter;
import marketcontrol.http.server.status.HttpStatus;
import marketcontrol.service.AuthService;

public class AuthHandler implements HttpHandler {

	private final AuthService authService;

	public AuthHandler(AuthService authService){
		this.authService = authService;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		String[] path = HttpRequests.segments(exchange);
		if (path.length < 2 || !"auth".equals(path[0])) {
			throw new ApiException(404, "NOT_FOUND", "Rota ou recurso não encontrado.");
		}
		HttpRequests.requireMethod(exchange, "POST");
		if (path.length == 2 && "login".equals(path[1])) {
			LoginRequest request = HttpRequests.readJson(exchange, LoginRequest.class);
			HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
					authService.authenticate(request.email(), request.password()));
			return;
		}
		if (path.length == 2 && "refresh".equals(path[1])) {
			RefreshRequest request = HttpRequests.readJson(exchange, RefreshRequest.class);
			HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(), authService.refresh(request.refreshToken()));
			return;
		}
		if (path.length == 2 && "logout".equals(path[1])) {
			RefreshRequest request = HttpRequests.readJson(exchange, RefreshRequest.class);
			authService.sessionService().logout(request.refreshToken());
			HttpResponseWriter.sendEmpty(exchange, HttpStatus.NO_CONTENT.getCode());
			return;
		}
		throw new ApiException(404, "NOT_FOUND", "Rota ou recurso não encontrado.");
	}

}
