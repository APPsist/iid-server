package de.appsist.service.iid.server;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import de.appsist.service.iid.server.handler.ActionHandler;
import de.appsist.service.iid.server.handler.ClientHandler;
import de.appsist.service.iid.server.handler.InternalBusHandler;
import de.appsist.service.iid.server.handler.LocalSessionHandler;
import de.appsist.service.iid.server.handler.SMSHandler;
import de.appsist.service.iid.server.handler.TabletClientHandler;

public class HandlerRegistry {
	public final static String SERVICE_ID = "appsist:service:iid";
	
	private final Vertx vertx;
	private final Container container;
	private final ConnectorRegistry connectors;
	
	private InternalBusHandler internalBusHandler = null;
	private LocalSessionHandler localSessionHandler = null;
	private ActionHandler actionHandler = null;
	private ClientHandler clientHandler = null; 
	private SMSHandler smsHandler = null;
	
	public HandlerRegistry(Vertx vertx, Container container, ConnectorRegistry connectors) {
		this.vertx = vertx;
		this.container = container;
		this.connectors = connectors;
	}
	
	public Vertx vertx() {
		return vertx;
	}
	
	public EventBus eventBus() {
		return vertx.eventBus();
	}
	
	public Container container() {
		return container;
	}
	
	public JsonObject serviceConfig(String serviceId) {
		return MainVerticle.getConfig().getServiceConfiguration(serviceId);
	}
	
	public void initInternalBusHandler() {
		internalBusHandler = new InternalBusHandler(this);
	}
	
	public InternalBusHandler internalBusHandler() {
		return internalBusHandler;
	}
	
	public void initLocalSessionHandler() {
		localSessionHandler = new LocalSessionHandler(connectors, this);
	}
	
	public LocalSessionHandler localSessionHandler() {
		return localSessionHandler;
	}

	public void initActionHandler() {
		actionHandler = new ActionHandler(vertx);
	}
	
	public ActionHandler actionHandler() {
		return actionHandler;
	}
	
	public void initClientHandler() {
		clientHandler = new TabletClientHandler(connectors, this);
	}
	
	public ClientHandler clientHandler() {
		return clientHandler;
	}
	
	public void initSMSHandler() {
		smsHandler = new SMSHandler(this, connectors);
	}
	
	public SMSHandler smsHandler() {
		return smsHandler;
	}
}
