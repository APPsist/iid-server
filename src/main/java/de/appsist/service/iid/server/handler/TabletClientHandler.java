package de.appsist.service.iid.server.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.service.auth.connector.model.User;
import de.appsist.service.auth.connector.model.View;
import de.appsist.service.iid.server.ConnectorRegistry;
import de.appsist.service.iid.server.HandlerRegistry;
import de.appsist.service.iid.server.model.LocalSession;

public class TabletClientHandler implements ClientHandler, ViewStateHandler {
	private static final Logger logger = LoggerFactory.getLogger(TabletClientHandler.class);
	private static final String DEVICE_CLASS = "tablet";
	
	private final ConnectorRegistry connectors;
	private final HandlerRegistry handlers;
	
	private final Map<View, TabletViewHandler> viewHandlers;
		
	public TabletClientHandler(ConnectorRegistry connectors, HandlerRegistry handlers) {
		this.connectors = connectors;
		this.handlers = handlers;
		
		viewHandlers = new HashMap<>();
		handlers.eventBus().registerHandler("appsist:service:iid:server", new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				JsonObject body = message.body();
				if (body == null) {
					message.reply(errorResponse(400, "Missing message body."));
					return;
				}
				String action = body.getString("action", "<none>");
				switch (action) {
				case "register":
					handleRegister(message);
					break;
				default:
					message.reply(errorResponse(400, "Invalid action command [action]: " + action));
				}
			}
		});
		
	}
	
	private void handleRegister(Message<JsonObject> message) {
		JsonObject body = message.body();
		String deviceId = body.getString("deviceId");
		if (deviceId == null) {
			message.reply(errorResponse(400, "Missing device identifier [deviceId]."));
			return;
		}
		
		View view = getViewForDevice(deviceId);
		TabletViewHandler viewHandler;
		if (view != null) {
			logger.debug("Received reconnecting request for view: " + view.getId());
			viewHandler = viewHandlers.get(view);
		} else {
			view = new View(UUID.randomUUID().toString(), DEVICE_CLASS, deviceId);
			logger.debug("Received registering request for device: " + deviceId);
			viewHandler = new TabletViewHandler(view, connectors, handlers);
			viewHandler.addViewStateHandler(this);
			viewHandlers.put(view, viewHandler);
		}
		viewHandler.init();

		JsonObject response = okResponse();
		LocalSession session = viewHandler.getSession();
		if (session != null) response.putObject("session", session.asJson());
		response.putObject("view", view.asJson());
		message.reply(response);
	}
	
	private View getViewForDevice(String deviceId) {
		for (View view : viewHandlers.keySet()) {
			if (view.getDeviceId().equals(deviceId)) {
				return view;
			}
		}
		return null;
	}
	
	private static JsonObject errorResponse(int code, String message) {
		return new JsonObject()
			.putString("status", "error")
			.putNumber("code", code)
			.putString("message", message);
	}
	
	private static JsonObject okResponse() {
		return new JsonObject()
			.putString("status", "ok");
	}

	@Override
	public void viewStateChanged(View view, ViewState state) {
		TabletViewHandler viewHandler = viewHandlers.get(view);
		switch (state) {
		case CONNECTED:
		case CONNECTING:
			// noting
			break;
		case DISCONNECTED:
			logger.debug("Removing handler for view: " + view.getId());
			viewHandler.removeViewStateListener(this);
			viewHandler.destroy();
			viewHandlers.remove(view);
		}
	}

	@Override
	public Set<? extends ViewHandler> getViewHandlersForSession(String sessionId) {
		Set<TabletViewHandler> viewHandlersForSession = new HashSet<>();
		for (TabletViewHandler viewHandler : viewHandlers.values()) {
			LocalSession session = viewHandler.getSession();
			if (session != null && session.getId().equals(sessionId)) {
				viewHandlersForSession.add(viewHandler);
			}
		}
		return viewHandlersForSession;
	}

	@Override
	public ViewHandler getViewHandler(String sessionId, String viewId) {
		for (TabletViewHandler viewHandler : viewHandlers.values()) {
			LocalSession session = viewHandler.getSession();
			if (session != null && session.getId().equals(sessionId) && viewHandler.getView().getId().equals(viewId)) {
				return viewHandler;
			}
		}
		return null;
	}

	@Override
	public User getUserForSession(String sessionId) {
		for (TabletViewHandler viewHandler : viewHandlers.values()) {
			LocalSession session = viewHandler.getSession();
			if (session != null && session.getId().equals(sessionId)) {
				return session.getUser();
			}
		}
		return null;
	}
	
}
