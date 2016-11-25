package de.appsist.service.iid.server;

import java.util.ArrayList;
import java.util.List;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import de.appsist.commons.misc.StatusSignalConfiguration;
import de.appsist.commons.misc.StatusSignalSender;
import de.appsist.service.iid.server.connector.IIDConnector;
import de.appsist.service.iid.server.model.AppsistEventAction;
import de.appsist.service.iid.server.model.AssistanceStep;
import de.appsist.service.iid.server.model.AssistanceStepBuilder;
import de.appsist.service.iid.server.model.ContentBody;
import de.appsist.service.iid.server.model.HttpPostAction;
import de.appsist.service.iid.server.model.InstructionItemBuilder;
import de.appsist.service.iid.server.model.LearningObject;
import de.appsist.service.iid.server.model.LearningObjectBuilder;
import de.appsist.service.iid.server.model.Level;
import de.appsist.service.iid.server.model.Notification;
import de.appsist.service.iid.server.model.SendMessageAction;
import de.appsist.service.iid.server.model.ServiceItem;
import de.appsist.service.iid.server.model.SiteOverview;
import de.appsist.service.iid.server.model.SiteOverviewBuilder;
import de.appsist.service.iid.server.model.StationInfo;
import de.appsist.service.iid.server.model.StationInfo.Panel;
import de.appsist.service.iid.server.model.StationInfoBuilder;
import de.appsist.service.sms.connector.SMSGatewayConnector;

/**
 * Main verticle of the server of the content interaction service.
 * @author simon.schwantzer(at)im-c.de
 */
public class MainVerticle extends Verticle {
	private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
	private static ModuleConfiguration config;
	private RouteMatcher routeMatcher;
	private ConnectorRegistry connectorRegistry;
	private HandlerRegistry handlerRegistry;
	
	@Override
	public void start() {
		config = new ModuleConfiguration(container.config());
		
		JsonArray deploys = config.getDeployments();
		if (deploys != null) for (Object deploy : deploys) {
			JsonObject deployConfig = (JsonObject) deploy;
			container.deployModule(deployConfig.getString("id"), deployConfig.getObject("config"));
		}
		
		connectorRegistry = new ConnectorRegistry(vertx);
		connectorRegistry.initAuthService(config.getServiceConfiguration("auth").getString("eb"));
		connectorRegistry.initCDSConnector(config.getServiceConfiguration("cds").getObject("http"));
		connectorRegistry.initSMSConnector(SMSGatewayConnector.DEFAULT_SERVICE_ID);

		handlerRegistry = new HandlerRegistry(vertx, container, connectorRegistry);
		handlerRegistry.initLocalSessionHandler();
		handlerRegistry.initInternalBusHandler();
		handlerRegistry.initClientHandler();
		handlerRegistry.initActionHandler();
		handlerRegistry.initSMSHandler();
				
		HttpServer httpServer = vertx.createHttpServer();
		initializeHTTPRouting(httpServer);

		// Initialize the event bus client bridge.
		JsonObject bridgeConfig = config.getEventBusBridgeConfiguration();
		if (bridgeConfig != null) {
			bridgeConfig.putString("prefix", "/eventbus");
			
			vertx.createSockJSServer(httpServer).bridge(bridgeConfig, bridgeConfig.getArray("inbound"), bridgeConfig.getArray("outbound"));
		}
		
		// Start web server.
		httpServer.listen(config.getWebserverPort());
		
		JsonObject statusSignalObject = config.getStatusSingalConfiguration();
		StatusSignalConfiguration statusSignalConfig;
		if (statusSignalObject != null) {
		  statusSignalConfig = new StatusSignalConfiguration(statusSignalObject);
		} else {
		  statusSignalConfig = new StatusSignalConfiguration();
		}

		StatusSignalSender statusSignalSender =
		  new StatusSignalSender("iid-server", vertx, statusSignalConfig);
		statusSignalSender.start();

		logger.debug("Inhalte-Interaktionsdienst (Server) has been initialized with the following configuration:\n" + config.getJson().encodePrettily());
	}
	
	@Override
	public void stop() {
		logger.debug("Inhalte-Interaktionsdienst (Server) has been stopped.");
	}
	
	/**
	 * Returns the module configuration.
	 * @return Module configuration.
	 */
	public static ModuleConfiguration getConfig() {
		return config;
	}


	/**
	 * Initialize the HTTP server endpoints.
	 * @param httpServer HTTP server to add endpoints. 
	 */
	private void initializeHTTPRouting(HttpServer httpServer) {
		final String basePath = config.getWebserverBasePath();
		routeMatcher = new BasePathRouteMatcher(basePath);
				
        if (config.isDebugModeEnabled()) {
			final IIDConnector iidConnector = new IIDConnector(vertx.eventBus(), IIDConnector.DEFAULT_ADDRESS);
			routeMatcher.post("/debug/generateItems", new Handler<HttpServerRequest>() {
	
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					List<ServiceItem> serviceItems = new ArrayList<ServiceItem>();
					HttpPostAction action = new HttpPostAction("http://localhost:8080/services/psd/startSupport/efefd3bb-5d78-4106-ad82-d50d1f22a1c8", new JsonObject());
					serviceItems.add(new InstructionItemBuilder().setId("real").setPriority(50).setService("iid").setTitle("DEBUGGIN").setAction(action).build());
					
					iidConnector.addServiceItems(sessionId, serviceItems, new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/sendNotification", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					Notification.Builder builder = new Notification.Builder("notification01", "Der Mülleimer brennt!", Level.WARNING);
					iidConnector.notify(sessionId, builder.build(), new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/dismissNotification", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					
					iidConnector.dismissNotification(sessionId, "notification01", new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/displayAssistance", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					
					AssistanceStepBuilder builder = new AssistanceStepBuilder();
					builder.setPreviousTitle("Erster Schritt");
					builder.setTitle("Zweiter Schritt");
					builder.setNextTitle("Nächster Schritt");
					builder.setProgress(0.5d);
					ContentBody contentBody = new ContentBody.Package("default");
					builder.setContentBody(contentBody);
					builder.setBackAction(new AppsistEventAction("myEvent", new JsonObject().putString("action", "back")));
					builder.setCloseAction(new SendMessageAction("appsist:service:foo", new JsonObject().putString("action", "close")));
					builder.setContactsAction(new HttpPostAction("/foo/bar", new JsonObject().putString("action", "contact")));
					builder.setNotesAction(new HttpPostAction("/foo/bar", new JsonObject().putString("action", "note")));
					builder.addActionButtonWithText("previous", "Zurück", new HttpPostAction("/foo/bar", new JsonObject().putString("action", "previous")));
					builder.addActionButtonWithText("next", "Weiter", new HttpPostAction("/foo/bar", new JsonObject().putString("action", "next")));
					 
					AssistanceStep assistanceStep = builder.build();
					iidConnector.displayAssistance(sessionId, "iid", assistanceStep, new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/displayLearningObject", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					
					LearningObjectBuilder builder = new LearningObjectBuilder();
					builder.setTitle("Mein Thema");
					LearningObject.Chapter chapter = new LearningObject.Chapter("Mein erstes Kapitel", new ContentBody.HTML("<p>Es war einmal ...</p>"));
					builder.addChapter(chapter);
					LearningObject lo = builder.build();
					iidConnector.displayLearningObject(sessionId, "iid", lo, new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/displaySiteOverview", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					
					SiteOverviewBuilder builder = new SiteOverviewBuilder();
					builder.setSite("Fräse");
					builder.addStation(new SiteOverview.Station("Station 20", Level.INFO, new ContentBody.HTML("<p>Alles normal.</p>"), null, null));
					builder.addStation(new SiteOverview.Station("Station 30", Level.WARNING, new ContentBody.HTML("<p>Gelegentlich elektrische Entladungen!</p>"), new HttpPostAction("/foo/bar", new JsonObject().putString("action", "learn something")), null));
					SiteOverview siteOverview = builder.build();
					
					iidConnector.displaySiteOverview(sessionId, "iid", siteOverview, new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/displayStationInfo", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					
					StationInfoBuilder builder = new StationInfoBuilder();
					builder.setSite("Fräse");
					builder.setStation("Station 20");
					builder.addPanel(new Panel("Druckventil", de.appsist.service.iid.server.model.Level.ERROR, new ContentBody.HTML("<p>Sind 52 bar zuviel?</p>"), null));
					builder.addPanel(new Panel("Motor", de.appsist.service.iid.server.model.Level.INFO, new ContentBody.HTML("<p>Die Station hat gar keinen Motor.</p>"), new SendMessageAction("appsist:service:foo", new JsonObject().putString("foo", "bar"))));
					StationInfo stationInfo = builder.build();
					iidConnector.displayStationInfo(sessionId, "iid", stationInfo, new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/endDisplay", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					String sessionId = request.params().get("sid");
					final HttpServerResponse response = request.response();
					iidConnector.endDisplay(sessionId, "iid", new AsyncResultHandler<Void>() {
						
						@Override
						public void handle(AsyncResult<Void> event) {
							if (event.succeeded()) {
								response.end();
							} else {
								response.setStatusCode(500).end(event.cause().getMessage());
							}
						}
					});
				}
			}).post("/debug/echo", new Handler<HttpServerRequest>() {
				
				@Override
				public void handle(HttpServerRequest request) {
					final HttpServerResponse response = request.response();
					request.bodyHandler(new Handler<Buffer>() {
						
						@Override
						public void handle(Buffer buffer) {
							logger.info("Retrieved debug echo: " + buffer.toString());
							response.end(buffer.toString());
						}
					});
				}
			});
		}
		
		httpServer.requestHandler(routeMatcher);
	}
}
 