package de.appsist.service.iid.server;

import org.vertx.java.core.json.JsonObject;

/**
 * Exceptions thrown when a operation fails.
 * @author simon.schwantzer(at)im-c.de
 */
public class OperationFailedException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private final int code;
	
	/**
	 * Creates an exception object.
	 * @param code Code for the exception, related to HTTP status codes.
	 * @param message Reason for the exception.
	 */
	public OperationFailedException(int code, String message) {
		super(message);
		this.code = code;
	}
	
	/**
	 * Creates a exception object.
	 * @param code Code for the exception, related to HTTP status codes.
	 * @param message Reason for the exception.
	 * @param t Throwable which caused the exception.
	 */
	public OperationFailedException(int code, String message, Throwable t) {
		super(message, t);
		this.code = code;
	}
	
	/**
	 * Returns the status code of the exception.
	 * @return Status code relate to HTTP status codes.
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Generates an error response to be send over the event bus.
	 * @return JSON object representing the exception.
	 */
	public JsonObject generateErrorResponse() {
		JsonObject response = new JsonObject();
		response.putString("status", "error");
		response.putNumber("code", code);
		String message = super.getMessage();
		if (message != null) response.putString("message", message);
		return response;
	}
}
