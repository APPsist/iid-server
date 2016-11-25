package de.appsist.service.iid.server.connector;

import org.vertx.java.core.json.JsonObject;

/**
 * Exception for HTTP error responses.
 * @author simon.schwantzer(at)im-c.de
 */
public class HttpException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final int statusCode;
	
	public HttpException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(400);
		builder.append("HTTPException: ").append(super.getMessage()).append(" (").append(statusCode).append(")");
		return builder.toString();
	}
	
	public int getStatusCode() {
		return statusCode;
	}
	
	public JsonObject asJson() {
		JsonObject json = new JsonObject();
		json.putNumber("code", statusCode);
		json.putString("message", getMessage());
		return json;
	}
}
