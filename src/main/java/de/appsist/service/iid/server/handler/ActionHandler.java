package de.appsist.service.iid.server.handler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.UUID;

import javax.xml.bind.DatatypeConverter;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * Helper for service actions.
 * @author simon.schwantzer(at)im-c.de
 */
public class ActionHandler {
	private static final Logger logger = LoggerFactory.getLogger(ActionHandler.class);
	
	final private Vertx vertx;
	
	public ActionHandler (Vertx vertx) {
		this.vertx = vertx;
	}
	
	/**
	 * Sends a HTTP POST request.
	 * @param url Address to send the request to.
	 * @param body Body to send.
	 * @throws IllegalArgumentException The given address is not a valid URL.
	 */
	public void sendHTTPPostRequest(String url, JsonObject body) throws IllegalArgumentException {
		final URL actionUrl;
		try {
			actionUrl = new URL(url);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
		
		final HttpClient httpClient = vertx.createHttpClient();
		httpClient.setHost(actionUrl.getHost());
		if (actionUrl.getPort() > 0) {
			httpClient.setPort(actionUrl.getPort());
		}
		httpClient.setSSL(actionUrl.getProtocol().equalsIgnoreCase("https"));
		StringBuilder pathBuilder = new StringBuilder();
		pathBuilder.append(actionUrl.getPath()).append("?").append(actionUrl.getQuery());
		
		HttpClientRequest clientRequest = httpClient.post(pathBuilder.toString(), new Handler<HttpClientResponse>() {
			
			@Override
			public void handle(final HttpClientResponse response) {
                        if (response.statusCode() != 200) {
					response.bodyHandler(new Handler<Buffer>() {
						
						@Override
						public void handle(Buffer buffer) {
							logger.warn("Failed to perform post request to " + actionUrl.toString()+ ". Response: " + buffer.toString()); 
						}
					});
				}
			}
		});
		clientRequest.end(body.encode());
	}
	
	/**
	 * Publishes an appsist event.
	 * @param modelId Appsist event model identifier.
	 * @param payload Payload for the event.
	 * @param sessionId Session identifier to add as meta information.
	 */
	public void publishAppsistEvent(String modelId, JsonObject payload, String sessionId) {
		JsonObject message = new JsonObject();
		JsonObject eventObject = generateAppsistEvent(modelId, sessionId, payload, message);
		vertx.eventBus().publish("appsist:event:" + modelId, eventObject);
	}
	
	/**
	 * Sends a message to a single endpoint using the event bus.
	 * @param address Address to send message to.
	 * @param message Message to send.
	 */
	public void sendMessage(String address, JsonObject message) {
		vertx.eventBus().send(address, message);
	}
	
	/**
	 * Send an event as specified in an action.
	 * @param action Action object as given in the service description.
	 * @param sessionId ID of the user session. May be <code>null</code>.
	 */
	public void performEventAction(JsonObject action, String sessionId) {
		logger.debug("Performing action: " + action);
		JsonObject message;
		switch (action.getString("type")) {
		case "appsist-event":
			String modelId  = action.getString("model");
			String eventModelId = "appsist:event:" + modelId;
			
			message = action.getObject("body").copy();
			JsonObject eventObject = generateAppsistEvent(eventModelId, sessionId, null, message);
			vertx.eventBus().publish(eventModelId, eventObject);
			break;
		case "event":
			String address = action.getString("address");
			message =  action.getObject("body").copy();
			vertx.eventBus().send(address, message);
			break;
		}
		
	}
	
	/**
	 * Generates an JsonObject representing an event as specified in APPsist.
	 * @param eventModelId ID of the event model implemented by this event.
	 * @param sessionId ID of the user session. May be <code>null</code>.
	 * @param payload Payload for the event. May be <code>null</code>.
	 * @param body If a event body is already available (not <code>null</code>), it will be used instead of a new JsonObject. 
	 * @return An object representing the 
	 */
	private static JsonObject generateAppsistEvent(String eventModelId, String sessionId, JsonObject payload, JsonObject body) {
		JsonObject eventObject = body != null ? body : new JsonObject();
		if (!eventObject.containsField("id")) {
			eventObject.putString("id", UUID.randomUUID().toString());
		}
		if (!eventObject.containsField("modelId")) {
			eventObject.putString("modelId", eventModelId);
		}
		if (!eventObject.containsField("created")) {
			Calendar cal = Calendar.getInstance();
			eventObject.putString("created", DatatypeConverter.printDateTime(cal));
		}
		if (!eventObject.containsField("session")) {
			eventObject.putString("session", sessionId);
		}
		if (payload != null) {
			eventObject.putObject("payload", payload);
		}
		return eventObject;
	}
}
