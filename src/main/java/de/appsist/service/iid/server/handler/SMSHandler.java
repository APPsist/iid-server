package de.appsist.service.iid.server.handler;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.service.auth.connector.model.User;
import de.appsist.service.iid.server.ConnectorRegistry;
import de.appsist.service.iid.server.HandlerRegistry;
import de.appsist.service.iid.server.model.Notification;

public class SMSHandler {
	private static final Logger logger = LoggerFactory.getLogger(SMSHandler.class);
	
	private final ConnectorRegistry connectors;
	private final HandlerRegistry handlers;
	
	public SMSHandler(HandlerRegistry handlers, ConnectorRegistry connectors) {
		this.connectors = connectors;
		this.handlers = handlers;
	}
	
	public void sendNotification(Notification notification, String sessionId) {
		User user = handlers.clientHandler().getUserForSession(sessionId);
		if (user != null && user.getMobile() != null) {
			StringBuilder builder = new StringBuilder();
			switch (notification.getLevel()) {
			case INFO:
				builder.append("[Info] ");
				break;
			case WARNING:
				builder.append("[Warnung] ");
				break;
			case ERROR:
				builder.append("[Fehler] ");
				break;
			}
			builder.append(notification.getMessage());
			connectors.smsConnector().sendMessage(user.getMobile(), builder.toString(), new AsyncResultHandler<Void>() {
				
				@Override
				public void handle(AsyncResult<Void> event) {
					if (event.failed()) {
						logger.warn("Failed to send SMS notification.", event.cause());
					} else {
						logger.debug("Succeded in sending notification as SMS.");
					}
				}
			});
		}
	}
}
