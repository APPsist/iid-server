package de.appsist.service.iid.server.handler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.service.auth.connector.AuthServiceConnector.AuthType;
import de.appsist.service.auth.connector.model.User;
import de.appsist.service.auth.connector.model.View;
import de.appsist.service.iid.server.ConnectorRegistry;
import de.appsist.service.iid.server.EventBusHelper;
import de.appsist.service.iid.server.FailResult;
import de.appsist.service.iid.server.HandlerRegistry;
import de.appsist.service.iid.server.MainVerticle;
import de.appsist.service.iid.server.OperationFailedException;
import de.appsist.service.iid.server.ValueAggregationHandler;
import de.appsist.service.iid.server.model.Action;
import de.appsist.service.iid.server.model.Activity;
import de.appsist.service.iid.server.model.AppsistEventAction;
import de.appsist.service.iid.server.model.AssistanceStep;
import de.appsist.service.iid.server.model.ContentBody;
import de.appsist.service.iid.server.model.ContentBody.Type;
import de.appsist.service.iid.server.model.HttpPostAction;
import de.appsist.service.iid.server.model.LearningObject;
import de.appsist.service.iid.server.model.LearningObject.Chapter;
import de.appsist.service.iid.server.model.LocalSession;
import de.appsist.service.iid.server.model.Location;
import de.appsist.service.iid.server.model.Notification;
import de.appsist.service.iid.server.model.Popup;
import de.appsist.service.iid.server.model.SendMessageAction;
import de.appsist.service.iid.server.model.ServiceCatalog;
import de.appsist.service.iid.server.model.SiteOverview;
import de.appsist.service.iid.server.model.StationInfo;

public class TabletViewHandler implements ViewHandler {
	private final static Logger logger = LoggerFactory.getLogger(TabletViewHandler.class);
	
	private static final JsonObject HEARTBEAT = new JsonObject().putString("action", "getStatus");
	
	// Default values if not configured.
	private static final long HEARTBEAT_INTERVAL = MILLISECONDS.convert(10, SECONDS);
	private static final long HEARTBEAT_TIMEOUT = 1000l;
	private static final long DISCONNECTION_TIMEOUT = MILLISECONDS.convert(2, MINUTES);
	
	
	private final ConnectorRegistry connectors;
	private final HandlerRegistry handlers;
	private final String clientAddress;
	private final View view;
	private final Handler<Message<JsonObject>> commandHandler;
	
	// User/Session Information
	private LocalSession session;
	private AuthType authType;
	private String authCode;
	private Location lastKnownLocation;
	private Activity userActivity;
	
	private Date lastAction;
	private Long heartBeatHandlerId;
	private ViewState viewState;
	private final Set<ViewStateHandler> connectionStateHandlers;
	private final Map<JsonObject, AsyncResultHandler<Void>> messagesCache;
	
	public TabletViewHandler(View view, ConnectorRegistry connectors, HandlerRegistry handlers) {
		this.handlers = handlers;
		this.connectors = connectors;
		this.view = view;
		connectionStateHandlers = new HashSet<>();
		messagesCache = new LinkedHashMap<>();
		viewState = ViewState.DISCONNECTED;
		lastAction = new Date();
		clientAddress = HandlerRegistry.SERVICE_ID + ":client:" + view.getId();
		lastKnownLocation = null;
		userActivity = Activity.UNKNOWN;
		
		commandHandler = new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> message) {
				JsonObject body = message.body();
				if (body == null) {
					message.reply(new JsonObject().putString("status", "error").putNumber("code", 400).putString("message", "Missing message body."));
					return;
				}
				String action = body.getString("action", "null");
				switch (action) {
				case "login":
					handleLogin(message);
					break;
				case "logout":
					handleLogout(message);
					break;
				case "performAction":
					handlePerformAction(message);
					break;
				case "setLocation":
					handleSetLocation(message);
					break;
				case "getFixLocations":
					handleGetFixLocations(message);
					break;
				case "setUserActivity":
					handleSetUserActivity(message);
					break;
				default:
					message.reply(EventBusHelper.errorResponse(400, "Unknown action command."));
				}
			}
		};
		
		logger.debug("Created view handler for view: " + view.getId());
	}
	
	public LocalSession getSession() {
		return session;
	}
	
	private void setSession(LocalSession session) {
		this.session = session;
		if (session != null) {
			logger.debug("Connected session " + session.getId() + " ("  + session.getUser().getId() + ") with view " + view.getId() + ".");
		} else {
			logger.debug("Disconnected session from view " + view.getId() + ".");
		}
	}
	
	public boolean hasSession() {
		return session != null;
	}
	
	public void init() {
		if (viewState != ViewState.DISCONNECTED) {
			// do nothing
			return;
		}
		
		// Listen to commands from client.
		handlers.eventBus().registerHandler("appsist:service:iid:server:" + view.getId(), commandHandler);
		// Start heart beat.
		JsonObject connConfig = MainVerticle.getConfig().getClientConnectionConfig();
		final long heartbeatInterval = connConfig.getLong("heartbeatInterval", HEARTBEAT_INTERVAL);
		final long heartbeatTimeout = connConfig.getLong("heartbeatTimeout", HEARTBEAT_TIMEOUT);
		final long disconnectionTimeout = connConfig.getLong("disconnectionTimeout", DISCONNECTION_TIMEOUT);
		handlers.vertx().setPeriodic(heartbeatInterval , new Handler<Long>() {
			@Override
			public void handle(Long handlerId) {
				heartBeatHandlerId = handlerId;
				final Date now = new Date();
				if (now.getTime() - lastAction.getTime() >= heartbeatInterval) {
					handlers.eventBus().sendWithTimeout(clientAddress, HEARTBEAT, heartbeatTimeout, new Handler<AsyncResult<Message<JsonObject>>>() {

						@Override
						public void handle(AsyncResult<Message<JsonObject>> request) {
							Message<JsonObject> message = request.result();
							if (request.succeeded()) {
								JsonObject body = message.body();
								String status = body.getString("status");
								if ("ok".equals(status)) {
									actionPerformed();
								} else {
									StringBuilder logMessageBuilder = new StringBuilder();
									logMessageBuilder
										.append("Received bad status for view ")
										.append(view.asJson())
										.append("(session: ").append(session.getId()).append("): ")
										.append(status);
									logger.warn(logMessageBuilder.toString());
								}
							} else {
								if (now.getTime() - lastAction.getTime() >= disconnectionTimeout) {
									updateViewState(ViewState.DISCONNECTED);
									handlers.vertx().cancelTimer(heartBeatHandlerId);
								} else {
									updateViewState(ViewState.CONNECTING);
								}
							}
						}
					});
					
				}
			}
		});
	}
	
	public void destroy() {
		if (heartBeatHandlerId != null) {
			handlers.vertx().cancelTimer(heartBeatHandlerId);
		}
		connectionStateHandlers.clear();
		handlers.eventBus().unregisterHandler("appsist:service:iid:server:" + view.getId(), commandHandler);
	}
	
	private void updateViewState(ViewState newState) {
		if (newState == viewState) return;
		viewState = newState;
		switch (viewState) {
		case DISCONNECTED:
			logger.debug("Disconnected view " + view.getId() + ".");
			purgeCachedMessages(messagesCache.entrySet().iterator());
			if (session != null) performLogout(new AsyncResultHandler<Void>() {
				
				@Override
				public void handle(AsyncResult<Void> event) {
					if (event.failed()) {
						logger.warn("Failed to logout disconnected user.", event.cause());
					}
				}
			});
			break;
		case CONNECTING:
			logger.debug("Failed to retrieve status for view " + view.getId() + ". Retrying ...");
			break;
		case CONNECTED:
			sendCachedMessages(messagesCache.entrySet().iterator());
			break;
		}
		
		for (ViewStateHandler handler : connectionStateHandlers) {
			handler.viewStateChanged(view, viewState);
		}
	}
	
	private void handleLogin(final Message<JsonObject> message) {
		actionPerformed();
		JsonObject body = message.body();
		final String userId = body.getString("userId");
		if (userId == null || userId.isEmpty()) {
			message.reply(EventBusHelper.errorResponse(400, "Missing user identifier (userId)."));
			return;
		}
		
		if (body.containsField("password")) {
			authType = AuthType.PASSWORD;
			authCode = body.getString("password");
		} else if (body.containsField("pin")) {
			authType = AuthType.PIN;
			authCode = body.getString("pin");
		} else if (body.containsField("hash")) {
			authType = AuthType.HASH;
			authCode = body.getString("hash");
		} else {
			message.reply(EventBusHelper.errorResponse(400, "Invalid authentication method."));
			return;
		}
		
		connectors.authService().authenticateUser(userId, authType, authCode, new AsyncResultHandler<User>() {
			
			@Override
			public void handle(AsyncResult<User> authRequest) {
				if (authRequest.succeeded()) {
					User user = authRequest.result();
					LocalSessionHandler sessionHandler = handlers.localSessionHandler();
					sessionHandler.registerView(user, user.loadToken(), view, new AsyncResultHandler<LocalSession>() {
						
						@Override
						public void handle(AsyncResult<LocalSession> sessionRequest) {
							if (sessionRequest.succeeded()) {
								LocalSession session = sessionRequest.result();
								setSession(session);
								JsonObject response = EventBusHelper.okResponse();
								response.putObject("session", session.asJson());
								message.reply(response);
							} else {
								message.reply(EventBusHelper.errorResponse(500, sessionRequest.cause().getMessage()));
							}
						}
					});
				} else {
					message.reply(EventBusHelper.errorResponse(500, authRequest.cause().getMessage()));
				}
			}
		});
	}
	
	private void handleLogout(final Message<JsonObject> message) {
		actionPerformed();
		performLogout(new AsyncResultHandler<Void>() {
			
			@Override
			public void handle(AsyncResult<Void> logoutRequest) {
				if (logoutRequest.succeeded()) {
					logger.debug("User logged out: " + session.getId());
					setSession(null);
					message.reply(EventBusHelper.okResponse());
				} else {
					message.reply(EventBusHelper.errorResponse(500, logoutRequest.cause().getMessage()));
					logger.warn("Failed to logout user.", logoutRequest.cause());
				}
			}
		});
	}
	
	private void performLogout(final AsyncResultHandler<Void> resultHandler) {
		if (session == null || session.getUser() == null) {
			resultHandler.handle(new AsyncResult<Void>() {
				@Override
				public boolean succeeded() {
					return false;
				}
				
				@Override
				public Void result() {
					return null;
				}
				
				@Override
				public boolean failed() {
					return true;
				}
				
				@Override
				public Throwable cause() {
					return new Throwable("No user session to log out.");
				}
			});
		} else {
			final User user = session.getUser();
			connectors.authService().generateTokenForUser(user.getId(), authType, authCode, new AsyncResultHandler<String>() {
				
				@Override
				public void handle(final AsyncResult<String> tokenRequest) {
					if (tokenRequest.succeeded()) {
						user.cacheToken(tokenRequest.result());
						handlers.localSessionHandler().removeView(session.getId(), user.loadToken(), view.getId(), new AsyncResultHandler<Void>() {
							
							@Override
							public void handle(final AsyncResult<Void> removeRequest) {
								resultHandler.handle(new AsyncResult<Void>() {
									
									@Override
									public boolean succeeded() {
										return removeRequest.succeeded();
									}
									
									@Override
									public Void result() {
										return null;
									}
									
									@Override
									public boolean failed() {
										return !succeeded();
									}
									
									@Override
									public Throwable cause() {
										return removeRequest.cause();
									}
								});
							}
						});
					} else {
						resultHandler.handle(new AsyncResult<Void>() {
							
							@Override
							public boolean succeeded() {
								return false;
							}
							
							@Override
							public Void result() {
								return null;
							}
							
							@Override
							public boolean failed() {
								return true;
							}
							
							@Override
							public Throwable cause() {
								return tokenRequest.cause();
							}
						});
					}
				}
			});
		}
	}
	
	private void handlePerformAction(final Message<JsonObject> message) {
		actionPerformed();
		final JsonObject body = message.body();
		JsonObject actionObject = body.getObject("actionToPerform");
		if (actionObject == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing action information (actionToPerform)."));
			return;
		}
		final Action action;
		try {
			action = Action.fromJson(actionObject);
		} catch (IllegalArgumentException e) {
			message.reply(EventBusHelper.errorResponse(400, "Invalid action information: " + e.getMessage()));
			return;
		}
		
		action.setSessionId(session.getId());
		connectors.authService().generateTokenForUser(session.getUser().getId(), authType, authCode, new AsyncResultHandler<String>() {
			
			@Override
			public void handle(AsyncResult<String> tokenRequest) {
				if (tokenRequest.failed()) {
					logger.warn("Failed to perform action request: Token generation failed.", tokenRequest.cause());
					message.reply(EventBusHelper.errorResponse(500, tokenRequest.cause().getMessage()));
					return;
				}
				
				action.setToken(tokenRequest.result());
				JsonObject response;
				switch (action.getType()) {
				case POST:
					final HttpPostAction postAction = (HttpPostAction) action;
					try {
						handlers.actionHandler().sendHTTPPostRequest(postAction.getAddress(), postAction.getBody());
						response = EventBusHelper.okResponse();
					} catch (IllegalArgumentException e) {
						response = EventBusHelper.errorResponse(400, "Failed to perform post request: " + e.getMessage());
					}
					break;
				case APPSIST_EVENT:
					AppsistEventAction appsistEventAction = (AppsistEventAction) action;
					handlers.actionHandler().publishAppsistEvent(appsistEventAction.getModel(), appsistEventAction.getPayload(), session.getId());
					response = EventBusHelper.okResponse();
					break;
				case MESSAGE:
					SendMessageAction eventAction = (SendMessageAction) action;
					handlers.actionHandler().sendMessage(eventAction.getAddress(), eventAction.getBody());
					response = EventBusHelper.okResponse();
					break;
				default:
					response = EventBusHelper.errorResponse(400, "Invalid action type.");
				}
				message.reply(response);
			}
		});
	}
	
	private void handleSetLocation(Message<JsonObject> message) {
		actionPerformed();
		JsonObject body = message.body();
		JsonObject locationObject = body.getObject("location");
		if (locationObject == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing location object [location]."));
			return;
		}
		String lastUpdate = DatatypeConverter.printDateTime(Calendar.getInstance());
		locationObject.putString("lastUpdate", lastUpdate);
		
		JsonObject response;
		Location location;
		try {
			location = new Location(locationObject);
			if (location.getType() == Location.Type.FIX) {
				lastKnownLocation = location;
			}
			response = EventBusHelper.okResponse();
		} catch (IllegalArgumentException e) {
			response = EventBusHelper.errorResponse(400, "Invalid location object: " + e.getMessage());
		}
		message.reply(response);
	}
	
	private void handleGetFixLocations(Message<JsonObject> message) {
		actionPerformed();
		JsonArray locations = new JsonArray();
		for (Location location : MainVerticle.getConfig().getLocations()) {
            locations.addObject(location.asJson());
		}
		JsonObject response = EventBusHelper.okResponse();
		response.putArray("locations", locations);
		message.reply(response);
	}
	
	private void handleSetUserActivity(Message<JsonObject> message) {
		actionPerformed();
		JsonObject body = message.body();
		String activityString = body.getString("activity");
		if (activityString == null) {
			message.reply(EventBusHelper.errorResponse(400, "Missing activity string [activity]."));
			return;
		}
        body.putString("sessionId", this.session.getId());
        handlers.eventBus().publish("appsist:event:userActivitySwitch", body);
		JsonObject response;
		try {
			userActivity = Activity.fromString(activityString);
			response = EventBusHelper.okResponse();
		} catch (IllegalArgumentException e) {
			response = EventBusHelper.errorResponse(400, "Invalid activity string [activity], expecting \"main\", \"side\", or \"unknown\".");
		}
		message.reply(response);
	}
	
	private void actionPerformed() {
		lastAction = new Date();
		updateViewState(ViewState.CONNECTED);
	}

	@Override
	public void addViewStateHandler(ViewStateHandler handler) {
		connectionStateHandlers.add(handler);
	}

	@Override
	public void removeViewStateListener(ViewStateHandler handler) {
		connectionStateHandlers.remove(handler);
	}

	private void sendCachedMessages(final Iterator<Entry<JsonObject, AsyncResultHandler<Void>>> iterator) {
		if (iterator.hasNext()) {
			Entry<JsonObject, AsyncResultHandler<Void>> entry = iterator.next();
			final JsonObject message = entry.getKey();
			final AsyncResultHandler<Void> resultHandler = entry.getValue();
			handlers.eventBus().send(clientAddress, message, new Handler<Message<JsonObject>>() {

				@Override
				public void handle(Message<JsonObject> result) {
					final JsonObject body = result.body();
					resultHandler.handle(new AsyncResult<Void>() {
						
						@Override
						public boolean succeeded() {
							return "ok".equals(body.getString("status"));
						}
						
						@Override
						public Void result() {
							return null;
						}
						
						@Override
						public boolean failed() {
							return !succeeded();
						}
						
						@Override
						public Throwable cause() {
							String message = body.getString("message");
							int code = body.getInteger("code");
							OperationFailedException e = new OperationFailedException(code, message);
							return e;
						}
					});
					sendCachedMessages(iterator);
				}
			});
		} else {
			logger.debug("Delivered " + messagesCache.size() + " cached messaged to view " + view.getId() + ".");
			messagesCache.clear();
		}
		
	}
	
	private void purgeCachedMessages(final Iterator<Entry<JsonObject, AsyncResultHandler<Void>>> iterator) {
		if (iterator.hasNext()) {
			Entry<JsonObject, AsyncResultHandler<Void>> entry = iterator.next();
			AsyncResultHandler<Void> resultHandler = entry.getValue();
			resultHandler.handle(new FailResult(new OperationFailedException(500, "Client disconnected.")));
			purgeCachedMessages(iterator);
		} else {
			logger.debug("Purged " + messagesCache.size() + " cached messaged, because view " + view.getId() + " disconnected.");
			messagesCache.clear();
		}
		
	}
	
	private void sendMessageToClient(JsonObject message, final AsyncResultHandler<Void> resultHandler) {
		switch (viewState) {
		case CONNECTED:
			// TODO Retrieve timeout from configuration.			
			handlers.eventBus().sendWithTimeout(clientAddress, message, HEARTBEAT_INTERVAL, new Handler<AsyncResult<Message<JsonObject>>>() {

				@Override
				public void handle(AsyncResult<Message<JsonObject>> event) {
					if (event.succeeded()) {
						final JsonObject body = event.result().body();
						resultHandler.handle(new AsyncResult<Void>() {
							
							@Override
							public boolean succeeded() {
								return "ok".equals(body.getString("status"));
							}
							
							@Override
							public Void result() {
								return null;
							}
							
							@Override
							public boolean failed() {
								return !succeeded();
							}
							
							@Override
							public Throwable cause() {
								String message = body.getString("message");
								int code = body.getInteger("code");
								OperationFailedException e = new OperationFailedException(code, message);
								return e;
							}
						});
					} else {
						resultHandler.handle(new FailResult(new OperationFailedException(500, "Message timed out.")));
					}
				}
			});
			// handlers.eventBus().send(clientAddress, message, new MessageResultHandler(resultHandler));
			break;
		case CONNECTING:
			logger.debug("Added item to messages cache for view " + view.getId() + ".");
			messagesCache.put(message, resultHandler);
			break;
		case DISCONNECTED:
			resultHandler.handle(new FailResult(new OperationFailedException(500, "The client is disconnected.")));
			break;
		}
	}
	
	@Override
	public void showNotification(Notification notification, final AsyncResultHandler<Void> resultHandler) {
		JsonObject message = new JsonObject();
		message.putString("action", "showNotification");
		message.putObject("notification", notification.asJson());
		
		sendMessageToClient(message, resultHandler);
	}

	@Override
    public void purgeNotifications(final AsyncResultHandler<Void> resultHandler) {
        JsonObject message = new JsonObject();
        message.putString("action", "purgeNotifications");
        
		sendMessageToClient(message, resultHandler);
    }

	@Override
	public void dismissNotification(String notificationId, final AsyncResultHandler<Void> resultHandler) {
		JsonObject message = new JsonObject();
		message.putString("action", "dismissNotification");
		message.putString("notificationId", notificationId);
		
		sendMessageToClient(message, resultHandler);
	}

	@Override
	public void updateCatalog(ServiceCatalog catalog, AsyncResultHandler<Void> resultHandler) {
		JsonObject message = new JsonObject();
		message.putString("action", "updateCatalog");
		message.putObject("catalog", catalog.asJson());

		sendMessageToClient(message, resultHandler);
	}

	@Override
	public void displayAssistance(final AssistanceStep assistance, final AsyncResultHandler<Void> resultHandler) {
		logger.debug("Received assistance step to display: " + assistance.asJson().encodePrettily());
		final JsonObject message = new JsonObject();
		message.putString("action", "displayAssistance");
		ContentBody content = assistance.getContent();
		if (content.getType() == Type.PACKAGE) {
			final ContentBody.Package packageContent = (ContentBody.Package) content;
			connectors.cdsConnector().retrieveContentManifest(packageContent.getPackageId(), new AsyncResultHandler<JsonObject>() {
				
				@Override
				public void handle(AsyncResult<JsonObject> descriptorRequest) {
					if (descriptorRequest.succeeded()) {
						JsonObject descriptor = descriptorRequest.result();
						logger.debug("Received content descriptor for assistance step: " + descriptor.encodePrettily());
						JsonObject assistanceStepObject = assistance.asJson();
						String basePath = handlers.serviceConfig("cds").getObject("http").getString("path") + "/" + packageContent.getPackageId() + "/";
						try {
							importContentDescriptorForAssistanceStep(assistanceStepObject, descriptor, basePath);
						} catch (IllegalArgumentException e) {
							logger.warn("Failed to import content package descriptor for package: " + packageContent.getPackageId(), e);
						}
						
						message.putObject("assistance", assistanceStepObject);
						sendMessageToClient(message, resultHandler);
					} else {
						logger.warn("Failed to retrieve content package: " + packageContent.getPackageId());
					}
				}
			});
		} else {
			message.putObject("assistance", assistance.asJson());
			sendMessageToClient(message, resultHandler);
		}
	}
	
	private static void importContentDescriptorForAssistanceStep(JsonObject assistanceStepObject, JsonObject descriptor, String baseUrl) throws IllegalArgumentException {
		JsonObject content = assistanceStepObject.getObject("content"); 
		String main = descriptor.getString("main");
		if (main != null) {
			URI uri;
			try {
				uri = new URI(main);
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("The [main] field contains no valid URI.", e);
			}
			content.putString("main", uri.isAbsolute() ? main : baseUrl + main);
			content.putString("mimeType", descriptor.getString("mimeType"));
		}
		
		String lastUpdate = descriptor.getString("lastUpdate");
		if (lastUpdate != null) content.putString("lastUpdate", lastUpdate);
		
		String version = descriptor.getString("version");
		if (version != null) content.putString("version", version);
		
		String title = descriptor.getString("title");
		if (title != null) {
			JsonObject titleContainer = assistanceStepObject.getObject("title");
			if (titleContainer == null) {
				titleContainer = new JsonObject();
				assistanceStepObject.putObject("title", titleContainer);
			}
			titleContainer.putString("current", title);
		}
		
		String info = descriptor.getString("info");
		if (info != null) assistanceStepObject.putString("info", info);
		
		String endorsement = descriptor.getString("endorsement");
		if (endorsement != null) assistanceStepObject.putString("endorsement", endorsement);
		
		String arid = descriptor.getString("arid");
		if (arid != null) assistanceStepObject.putString("arid", arid);
		
		JsonArray newWarnings = descriptor.getArray("warnings");
		if (newWarnings != null) {
			JsonArray existingWarnings = assistanceStepObject.getArray("warnings");
			if (existingWarnings == null) {
				existingWarnings = new JsonArray();
				assistanceStepObject.putArray("warnings", existingWarnings);
			}
			for (Object entry : newWarnings) {
				existingWarnings.add(entry);
			}
			for (Object entry : existingWarnings) {
				JsonObject warning = (JsonObject) entry;
				String icon = warning.getString("icon");
				if (icon != null) {
					URI uri;
					try {
						uri = new URI(icon);
					} catch (URISyntaxException e) {
						throw new IllegalArgumentException("The [icon] field contains no valid URI.", e);
					}
					warning.putString("icon", uri.isAbsolute() ? icon : baseUrl + icon);
				}
			}
		}
	}

	@Override
	public void displayLearningObject(final LearningObject learningObject, final AsyncResultHandler<Void> resultHandler) {
		final JsonObject message = new JsonObject();
		message.putString("action", "displayLearningObject");
		
		List<String> contentPackageIds = new ArrayList<>();
		for (final Chapter chapter : learningObject.getChapters()) {
			if (chapter.getBody().getType() == Type.PACKAGE) {
				ContentBody.Package packageContent = (ContentBody.Package) chapter.getBody();
				contentPackageIds.add(packageContent.getPackageId());
			}
		}
		
		if (contentPackageIds.isEmpty()) {
			// No need to retrieve package metadata, just forward object.
			message.putObject("learningObject", learningObject.asJson());
			sendMessageToClient(message, resultHandler);
			return;
		}
		
		ValueAggregationHandler<String, JsonObject> aggregationHandler = new ValueAggregationHandler<String, JsonObject>(contentPackageIds, new AsyncResultHandler<Map<String, AsyncResult<JsonObject>>>() {

			@Override
			public void handle(final AsyncResult<Map<String, AsyncResult<JsonObject>>> aggregatedRequest) {
				if (aggregatedRequest.succeeded()) {
					Map<String, AsyncResult<JsonObject>> packageDescriptorRequests = aggregatedRequest.result();
					for (final Chapter chapter : learningObject.getChapters()) {
						ContentBody contentBody = chapter.getBody();
						if (contentBody.getType() == Type.PACKAGE) {
							ContentBody.Package packageContent = (ContentBody.Package) contentBody;
							JsonObject descriptor = packageDescriptorRequests.get(packageContent.getPackageId()).result();
							String basePath = handlers.serviceConfig("cds").getObject("http").getString("path") + "/" + packageContent.getPackageId() + "/";
							try {
								importContentDescriptor(contentBody.asJson(), chapter.asJson(), descriptor, basePath);
							} catch (IllegalArgumentException e) {
								logger.warn("Failed to import content package descriptor for package: " + packageContent.getPackageId(), e);
							}
						}
					}
					message.putObject("learningObject", learningObject.asJson());
					sendMessageToClient(message, resultHandler);
				} else {
					final Throwable cause = aggregatedRequest.cause();
					logger.warn("Failed to retrieve content package.", cause);
					if (resultHandler != null) resultHandler.handle(new FailResult(cause));
				}
			}
		});
		
		for (String packageId : contentPackageIds) {
			connectors.cdsConnector().retrieveContentManifest(packageId, aggregationHandler.getRequestHandler(packageId));
		}
	}
	
	private static void importContentDescriptor(JsonObject contentBody, JsonObject parent, JsonObject descriptor, String baseUrl) throws IllegalArgumentException {
		String main = descriptor.getString("main");
		if (main != null) {
			URI uri;
			try {
				uri = new URI(main);
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("The [main] field contains no valid URI.", e);
			}
			contentBody.putString("main", uri.isAbsolute() ? main : baseUrl + main);
            contentBody.putString("mimeType", descriptor.getString("mimeType"));
            contentBody.putString("info", descriptor.getString("info", ""));
            contentBody.putString("title", descriptor.getString("title", ""));
        }
        else {
            contentBody.putString("mimeType", descriptor.getString("mimeType"));
            contentBody.putString("info", descriptor.getString("info", ""));
            contentBody.putString("title", descriptor.getString("title", ""));
		}
		
		String lastUpdate = descriptor.getString("lastUpdate");
		if (lastUpdate != null) contentBody.putString("lastUpdate", lastUpdate);
		
		String version = descriptor.getString("version");
		if (version != null) contentBody.putString("version", version);
		
		String title = descriptor.getString("title");
		if (title != null) {
			parent.putString("caption", title);
		}
	}

	@Override
	public void displaySiteOverview(final SiteOverview siteOverview, final AsyncResultHandler<Void> resultHandler) {
		final JsonObject message = new JsonObject();
		message.putString("action", "displaySiteOverview");
		message.putObject("siteOverview", siteOverview.asJson());

		sendMessageToClient(message, resultHandler);
	}

	@Override
	public void displayStationInfo(final StationInfo stationInfo, final AsyncResultHandler<Void> resultHandler) {
		final JsonObject message = new JsonObject();
		message.putString("action", "displayStationInfo");
		message.putObject("stationInfo", stationInfo.asJson());

		sendMessageToClient(message, resultHandler);
	}

	@Override
	public void releaseView(final AsyncResultHandler<Void> resultHandler) {
		final JsonObject message = new JsonObject();
		message.putString("action", "releaseView");
		
		sendMessageToClient(message, resultHandler);
	}

	@Override
	public void displayPopup(final Popup popup, final AsyncResultHandler<Void> resultHandler) {
		final JsonObject message = new JsonObject();
		message.putString("action", "displayPopup");
		
		final ContentBody content = popup.getBody();
		if (content.getType() == Type.PACKAGE) {
			final ContentBody.Package packageContent = (ContentBody.Package) content;
			connectors.cdsConnector().retrieveContentManifest(packageContent.getPackageId(), new AsyncResultHandler<JsonObject>() {
				
				@Override
				public void handle(AsyncResult<JsonObject> descriptorRequest) {
					JsonObject popupJson = popup.asJson();
					JsonObject contentJson = content.asJson();
					String basePath = handlers.serviceConfig("cds").getObject("http").getString("path") + "/" + packageContent.getPackageId() + "/";
					if (descriptorRequest.succeeded()) {
						JsonObject descriptor = descriptorRequest.result();
						for (String field : descriptor.getFieldNames()) {
							switch (field) {
							case "title":
								String title = descriptor.getString("title");
								popupJson.putString("title", popupJson.getString("title") + ": " + title);
								break;
							case "main":
								String main = descriptor.getString("main");
								URI uri;
								try {
									uri = new URI(main);
								} catch (URISyntaxException e) {
									throw new IllegalArgumentException("The [main] field contains no valid URI.", e);
								}
								contentJson.putString("main", uri.isAbsolute() ? main : basePath + main);
								break;
							default:
								contentJson.putValue(field, descriptor.getValue(field));
							}
						}
						message.putObject("popup", popup.asJson());
						sendMessageToClient(message, resultHandler);
					} else {
						logger.warn("Failed to retrieve content package: " + packageContent.getPackageId());
					}
				}
			});
		} else {
			message.putObject("popup", popup.asJson());
			sendMessageToClient(message, resultHandler);
		}
	}

	@Override
	public View getView() {
		return view;
	}

	@Override
	public Location getLastKnownLocation() {
		return lastKnownLocation;
	}
	
	@Override
	public Activity getUserActivity() {
		return userActivity;
	}
}
