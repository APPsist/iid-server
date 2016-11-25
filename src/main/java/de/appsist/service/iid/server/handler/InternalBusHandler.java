package de.appsist.service.iid.server.handler;

import static de.appsist.service.iid.server.EventBusHelper.errorResponse;
import static de.appsist.service.iid.server.EventBusHelper.okResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.service.iid.server.EventBusHelper;
import de.appsist.service.iid.server.HandlerRegistry;
import de.appsist.service.iid.server.MainVerticle;
import de.appsist.service.iid.server.OperationFailedException;
import de.appsist.service.iid.server.ResultAggregationHandler;
import de.appsist.service.iid.server.model.Activity;
import de.appsist.service.iid.server.model.AssistanceStep;
import de.appsist.service.iid.server.model.LearningObject;
import de.appsist.service.iid.server.model.LocalSession;
import de.appsist.service.iid.server.model.Location;
import de.appsist.service.iid.server.model.Notification;
import de.appsist.service.iid.server.model.Popup;
import de.appsist.service.iid.server.model.ServiceCatalog;
import de.appsist.service.iid.server.model.ServiceItem;
import de.appsist.service.iid.server.model.SiteOverview;
import de.appsist.service.iid.server.model.StationInfo;

/**
 * Handler for messages sent from other services.
 * @author simon.schwantzer(at)im-c.de
 */
public class InternalBusHandler {
	private static final Logger logger = LoggerFactory.getLogger(InternalBusHandler.class); 
	private static final String SERVICE_ID = "appsist:service:iid";
	private final HandlerRegistry handlers;
	
	private static class AggregatedMessageResponseHandler extends ResultAggregationHandler<ViewHandler> {
		public AggregatedMessageResponseHandler(Set<ViewHandler> requesters, final Message<JsonObject> messageToReply) {
			super(requesters, new MessageResponseHandler(messageToReply));
		}
	} 
	
	public static class MessageResponseHandler implements AsyncResultHandler<Void> {
		private Message<JsonObject> messageToReply;
		public MessageResponseHandler(Message<JsonObject> message) {
			messageToReply = message;
		}

		@Override
		public void handle(AsyncResult<Void> result) {
			if (result.succeeded()) {
				messageToReply.reply(EventBusHelper.okResponse());
			} else {
				Throwable cause = result.cause();
				if (cause != null) {
					if (cause instanceof OperationFailedException) {
						OperationFailedException e = (OperationFailedException) cause;
						messageToReply.reply(e.generateErrorResponse());
					} else {
						messageToReply.reply(EventBusHelper.errorResponse(500, cause.getMessage()));
					}
				} else {
					messageToReply.reply(EventBusHelper.errorResponse(500, "Operation failed by unknown reason."));
				}
			}
        }
	}
	
	public InternalBusHandler(HandlerRegistry handlerRegistry) {
		this.handlers = handlerRegistry;
		// this.logger = handlerRegistry.getLogger();
        handlerRegistry.eventBus().registerHandler(SERVICE_ID, new Handler<Message<JsonObject>>()
        {
			
			@Override
			public void handle(Message<JsonObject> message) {
				JsonObject body = message.body();
				String action = body.getString("action");
				if (action == null) {
					message.reply(EventBusHelper.errorResponse(400, "Missing action command."));
					return;
				}
				
				switch (action) {
				case "addServiceItems":
					handleAddServiceItems(message);
					break;
				case "purgeServiceItems":
					handlePurgeServiceItems(message);
					break;
				case "notify":
					handleNotify(message);
					break;
				case "dismissNotification":
					handleDismissNotification(message);
					break;
				case "displayAssistance":
					handleDisplayAssistance(message);
					break;
				case "displayLearningContent":
					handleDisplayLearningObject(message);
					break;
				case "displaySiteOverview":
					handleDisplaySiteOverview(message);
					break;
				case "displayStationInfo":
					handleDisplayStationInfo(message);
					break;
				case "endDisplay":
					handleEndDisplay(message);
					break;
				case "displayPopup":
					handleDisplayPopup(message);
					break;
				case "getLastKnownLocation":
					handleGetLastKnownLocation(message);
					break;
				case "getUserActivity":
					handleGetUserActivity(message);
					break;
                    case "purgeNotifications" :
                        handlePurgeNotifications(message);
                        break;
				default:
                    logger.warn("Received invalid action command: " + action);
					message.reply(EventBusHelper.errorResponse(400, "Invalid action command."));
				}
			}
		});		
	}
	
	private void handleAddServiceItems(final Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		LocalSession session = handlers.localSessionHandler().getSession(sessionId);
		JsonArray itemsArray = body.getArray("items");
		if (itemsArray == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing items to add (items)."));
			return;
		}
		List<ServiceItem> items = new ArrayList<ServiceItem>();
		for (Object itemObject : itemsArray) {
			ServiceItem item;
			try {
				item = new ServiceItem((JsonObject) itemObject);
				items.add(item);
			} catch (IllegalArgumentException | ClassCastException e) {
				message.reply(EventBusHelper.errorResponse(400, "Invalid item: " + e.getMessage()));
				return;
			}
		}
		
		Map<String, List<ServiceItem>> itemsForCatalog = new HashMap<>();
		for (ServiceItem item : items) {
			String catalogId = item.getCatalogId();
			List<ServiceItem> itemCatalog = itemsForCatalog.get(catalogId);
			if (itemCatalog == null) {
				itemCatalog = new ArrayList<ServiceItem>();
				itemsForCatalog.put(catalogId, itemCatalog);
			}
			itemCatalog.add(item);
		}
		Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
		Set<String> catalogIds = itemsForCatalog.keySet();
		ResultAggregationHandler<String> completeHandler = new ResultAggregationHandler<String>(catalogIds, new MessageResponseHandler(message));
		for (String catalogId : catalogIds) {
			ServiceCatalog catalog = session.getServiceCatalog(catalogId);
			if (catalog == null) {
				catalog = new ServiceCatalog(catalogId);
				session.addServiceCatalog(catalog);
			}
			catalog.addItems(itemsForCatalog.get(catalogId));
			
			AsyncResultHandler<Void> resultHandler = completeHandler.getRequestHandler(catalogId);
			final ResultAggregationHandler<ViewHandler> aggregationHandler = new ResultAggregationHandler<ViewHandler>(clientConnections, resultHandler);
			for (ViewHandler clientConnection : clientConnections) {
				clientConnection.updateCatalog(catalog, aggregationHandler.getRequestHandler(clientConnection));
			}
		}
	}
	
	private void handlePurgeServiceItems(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		LocalSession session = handlers.localSessionHandler().getSession(sessionId);
		String serviceId = body.getString("serviceId");
		if (serviceId == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing service id (serviceId)."));
			return;
		}
		
		Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
		Set<String> catalogIds = session.getServiceCatalogIds();
		ResultAggregationHandler<String> completeHandler = new ResultAggregationHandler<String>(catalogIds, new MessageResponseHandler(message));
		for (String catalogId : catalogIds) {
			ServiceCatalog catalog = session.getServiceCatalog(catalogId);
			catalog.removeItemsOfService(serviceId);
			
			AsyncResultHandler<Void> resultHandler = completeHandler.getRequestHandler(catalogId);
			final ResultAggregationHandler<ViewHandler> aggregationHandler = new ResultAggregationHandler<ViewHandler>(clientConnections, resultHandler);
			for (ViewHandler clientConnection : clientConnections) {
				clientConnection.updateCatalog(catalog, aggregationHandler.getRequestHandler(clientConnection));
			}
		}
	}
	
	private void handleNotify(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		JsonObject notificationObject = body.getObject("notification");
		if (notificationObject == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing notification field (notification)."));
			return;
		}
		Notification notification;
		try {
			notification = new Notification(notificationObject);
		} catch (IllegalArgumentException e) {
			message.reply(EventBusHelper.errorResponse(400, e.getMessage()));
			return;
		}
		String viewId = body.getString("viewId");
		ClientHandler clientHandler = handlers.clientHandler();
		if (viewId != null) {
			ViewHandler viewHandler = clientHandler.getViewHandler(sessionId, viewId);
			
			if (viewHandler != null) {
				viewHandler.showNotification(notification, new MessageResponseHandler(message));
			} else {
				message.reply(EventBusHelper.errorResponse(404, "View not found."));
				return;				
			}
		} else {
			Set<ViewHandler> viewHandlers = Collections.unmodifiableSet(clientHandler.getViewHandlersForSession(sessionId));
			final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(viewHandlers, message);
			for (ViewHandler viewHandler : viewHandlers) {
				viewHandler.showNotification(notification, resultAggregationHandler.getRequestHandler(viewHandler));
			}
			if (MainVerticle.getConfig().sendSMSNotifications()) {
				handlers.smsHandler().sendNotification(notification, sessionId);
			}
		}
	}

	private void handleDismissNotification(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		String notificationId = body.getString("notificationId");
		if (notificationId == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing notification identifierion (notificationId)."));
			return;
		}

		Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
		final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(clientConnections, message);
		for (ViewHandler connection : clientConnections) {
			connection.dismissNotification(notificationId, resultAggregationHandler.getRequestHandler(connection));
		}
	}

    private void handlePurgeNotifications(Message<JsonObject> message)
    {
        final JsonObject body = message.body();
        String sessionId = body.getString("sessionId");
        if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
            message.reply(EventBusHelper.errorResponse(400,
                    "Missing or wrong session information (sessionId)."));
            return;
        }

        Set<ViewHandler> clientConnections = Collections
                .unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
        final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(
                clientConnections, message);
        for (ViewHandler connection : clientConnections) {
            connection.purgeNotifications(resultAggregationHandler.getRequestHandler(connection));
        }
    }

	private void handleDisplayAssistance(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		String serviceId = body.getString("serviceId");
		if (serviceId == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing service id (serviceId)."));
			return;
		}
		String viewId = body.getString("viewId");

		JsonObject assistanceObject = body.getObject("assistance");
		if (assistanceObject == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing assistance step information (assistance)."));
			return;
		}
		AssistanceStep assistance;
		try {
			assistance = new AssistanceStep(assistanceObject);
		} catch (IllegalArgumentException e) {
			message.reply(EventBusHelper.errorResponse(400, "Invalid assistance information: " + e.getMessage()));
			return;
		}
		
		if (viewId != null) {
			ViewHandler connection = handlers.clientHandler().getViewHandler(sessionId, viewId);
			if (connection != null) {
				connection.displayAssistance(assistance, new MessageResponseHandler(message));
			} else {
				message.reply(EventBusHelper.errorResponse(404, "View not found."));
				return;				
			}
		} else {
			Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
			final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(clientConnections, message);
			for (ViewHandler connection : clientConnections) {
				connection.displayAssistance(assistance, resultAggregationHandler.getRequestHandler(connection));
			}
		}
		message.reply(EventBusHelper.okResponse());
	}
	
	private void handleDisplayLearningObject(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		String serviceId = body.getString("serviceId");
		if (serviceId == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing service id (serviceId)."));
			return;
		}
		String viewId = body.getString("viewId");

		JsonObject learningObjectJson = body.getObject("learningObject");
		if (learningObjectJson == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing learning object to display (learningObject)."));
			return;
		}
		LearningObject learningObject;
		try {
			learningObject = new LearningObject(learningObjectJson);
		} catch (IllegalArgumentException e) {
			message.reply(EventBusHelper.errorResponse(400, "Invalid learning object information: " + e.getMessage()));
			return;
		}
		
		if (viewId != null) {
			ViewHandler connection = handlers.clientHandler().getViewHandler(sessionId, viewId);
			if (connection != null) {
				connection.displayLearningObject(learningObject, new MessageResponseHandler(message));
			} else {
				message.reply(EventBusHelper.errorResponse(404, "View not found."));
				return;				
			}
		} else {
			Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
			final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(clientConnections, message);
			for (ViewHandler connection : clientConnections) {
				connection.displayLearningObject(learningObject, resultAggregationHandler.getRequestHandler(connection));
			}
		}
	}
	
	private void handleDisplaySiteOverview(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		String serviceId = body.getString("serviceId");
		if (serviceId == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing service id (serviceId)."));
			return;
		}
		String viewId = body.getString("viewId");

		JsonObject siteOverviewObject = body.getObject("siteOverview");
		if (siteOverviewObject == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing site overview to display (siteOverview)."));
			return;
		}
		SiteOverview siteOverview;
		try {
			siteOverview = new SiteOverview(siteOverviewObject);
		} catch (IllegalArgumentException e) {
			message.reply(EventBusHelper.errorResponse(400, "Invalid site overview information: " + e.getMessage()));
			return;
		}
		
		if (viewId != null) {
			ViewHandler connection = handlers.clientHandler().getViewHandler(sessionId, viewId);
			if (connection != null) {
				connection.displaySiteOverview(siteOverview, new MessageResponseHandler(message));
			} else {
				message.reply(EventBusHelper.errorResponse(404, "View not found."));
				return;				
			}
		} else {
			Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
			final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(clientConnections, message);
			for (ViewHandler connection : clientConnections) {
				connection.displaySiteOverview(siteOverview, resultAggregationHandler.getRequestHandler(connection));
			}
		}
	}
	
	private void handleDisplayStationInfo(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		String serviceId = body.getString("serviceId");
		if (serviceId == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing service id (serviceId)."));
			return;
		}
		String viewId = body.getString("viewId");

		JsonObject stationInfoObject = body.getObject("stationInfo");
		if (stationInfoObject == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing station info to display (stationInfo)."));
			return;
		}
		StationInfo stationInfo;
		try {
			stationInfo = new StationInfo(stationInfoObject);
		} catch (IllegalArgumentException e) {
			message.reply(EventBusHelper.errorResponse(400, "Invalid station info data: " + e.getMessage()));
			return;
		}
		
		if (viewId != null) {
			ViewHandler connection = handlers.clientHandler().getViewHandler(sessionId, viewId);
			if (connection != null) {
				connection.displayStationInfo(stationInfo, new MessageResponseHandler(message));
			} else {
				message.reply(EventBusHelper.errorResponse(404, "View not found."));
				return;				
			}
		} else {
			Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
			final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(clientConnections, message);
			for (ViewHandler connection : clientConnections) {
				connection.displayStationInfo(stationInfo, resultAggregationHandler.getRequestHandler(connection));
			}
		}
	}
	
	private void handleEndDisplay(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		String viewId = body.getString("viewId");
		if (viewId != null) {
			ViewHandler connection = handlers.clientHandler().getViewHandler(sessionId, viewId);
			if (connection != null) {
				connection.releaseView(new MessageResponseHandler(message));
			} else {
				message.reply(EventBusHelper.errorResponse(404, "View not found."));
				return;				
			}
		} else {
			Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
			final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(clientConnections, message);
			for (ViewHandler connection : clientConnections) {
				connection.releaseView(resultAggregationHandler.getRequestHandler(connection));
			}
		}
	}
	
	private void handleDisplayPopup(Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}
		String serviceId = body.getString("serviceId");
		if (serviceId == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing service id (serviceId)."));
			return;
		}
		String viewId = body.getString("viewId");
		JsonObject popupObject = body.getObject("popup");
		if (popupObject == null) {
			message.reply(errorResponse(400, "Missing popup to display (popup)."));
			return;
		}
		Popup popup;
		try {
			popup = new Popup(popupObject);
		} catch (IllegalArgumentException e) {
			message.reply(errorResponse(400, "Invalid popup object: " + e.getMessage()));
			return;
		}
		
		if (viewId != null) {
			ViewHandler connection = handlers.clientHandler().getViewHandler(sessionId, viewId);
			if (connection != null) {
				connection.displayPopup(popup, new MessageResponseHandler(message));
			} else {
				message.reply(errorResponse(404, "View not found."));
				return;				
			}
		} else {
			Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
			final ResultAggregationHandler<ViewHandler> resultAggregationHandler = new AggregatedMessageResponseHandler(clientConnections, message);
			for (ViewHandler connection : clientConnections) {
				connection.displayPopup(popup, resultAggregationHandler.getRequestHandler(connection));
			}
		}
	}
	
	private void handleGetLastKnownLocation(final Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}

		Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
		
		Location lastKnownLocation = null;
		for (ViewHandler connection : clientConnections) {
			Location location = connection.getLastKnownLocation();
			if (location == null || location.getLastUpdate() == null) continue;
			if (lastKnownLocation == null || location.getLastUpdate().after(lastKnownLocation.getLastUpdate())) {
				lastKnownLocation = location;
			}	
		}
		JsonObject response = okResponse();
		if (lastKnownLocation != null) {
			response.putObject("location", lastKnownLocation.asJson());
		}
		message.reply(response);
	}
	
	private void handleGetUserActivity(final Message<JsonObject> message) {
		final JsonObject body = message.body();
		String sessionId = body.getString("sessionId");
		if (sessionId == null || !handlers.localSessionHandler().hasSession(sessionId)) {
			message.reply(EventBusHelper.errorResponse(400, "Missing or wrong session information (sessionId)."));
			return;
		}

		Set<ViewHandler> clientConnections = Collections.unmodifiableSet(handlers.clientHandler().getViewHandlersForSession(sessionId));
		
		Activity activity = Activity.UNKNOWN;
		for (ViewHandler connection : clientConnections) {
			Activity clientActivity = connection.getUserActivity();
			if (clientActivity != Activity.UNKNOWN) {
				activity = clientActivity;
			}
		}
		
		JsonObject response = okResponse();
		response.putString("activity", activity.toString());
		message.reply(response);
	}
}