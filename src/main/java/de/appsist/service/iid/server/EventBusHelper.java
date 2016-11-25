package de.appsist.service.iid.server;

import org.vertx.java.core.json.JsonObject;

public class EventBusHelper {
	private EventBusHelper() {
		// nothing
	};

	
	/**
	 * Prepares a response for a reply indication a successful processing of the request.
	 * @return JSON object to be sent as reply.
	 */
	public static JsonObject okResponse() {
		JsonObject response = new JsonObject();
		response.putString("status", "ok");
		return response;
	}
	
	/**
	 * Prepares a error message to be send as reply.
	 * @param code Error code, related to HTTP error codes.
	 * @param message Error message. May be <code>null</code>.
	 * @return JSON object to be sent as reply.
	 */
	public static JsonObject errorResponse(int code, String message) {
		JsonObject response = new JsonObject();
		response.putString("status", "error");
		response.putNumber("code", code);
		if (message != null) response.putString("message", message);
		return response;
	}
}
