package de.appsist.service.iid.server;

import java.util.ArrayList;
import java.util.List;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import de.appsist.service.iid.server.model.Location;

/**
 * Wrapper for the module configuration.
 * @author simon.schwantzer(at)im-c.de
 */
public class ModuleConfiguration {
	private final JsonObject config;
	private final List<Location> locations;
	
	/**
	 * Creates a wrapper for the given configuration object. 
	 * @param config JSON object containing the module configuration. 
	 * @throws IllegalArgumentException The given configuration is not valid.
	 */
	public ModuleConfiguration(JsonObject config) throws IllegalArgumentException {
		if (config == null) {
			throw new IllegalArgumentException("Configuration is but may not be null.");
		}
		validateConfiguration(config);
		this.config = config;
		
		locations = new ArrayList<>();
		JsonArray locationsArray = config.getArray("locations");
		if (locationsArray != null) for (Object entry : locationsArray) {
			locations.add(new Location((JsonObject) entry));
		}
	}
	
	/**
	 * Validate the module configuration.
	 * @param config JSON object to validate.
	 * @throws IllegalArgumentException The JSON object is no valid module configuration. 
	 */
	private void validateConfiguration(JsonObject config) throws IllegalArgumentException {
		JsonObject webserver = config.getObject("webserver");
		if (webserver == null) {
			throw new IllegalArgumentException("Configuration for web server [webserver] is missing.");
		}
		
		if (config.getObject("services") == null) {
			throw new IllegalArgumentException("Configuration for services [services] is missing.");
		}
	}
	
	/**
	 * Returns the JSON object wrapped.
	 * @return JSON object containing the module configuration.
	 */
	public JsonObject getJson() {
		return config;
    }
	
	/**
	 * Returns the list of deployments to be performed.
	 * @return Array with deployment configurations. May be <code>null</code> or empty.
	 */
	public JsonArray getDeployments() {
		return config.getArray("deploys");
	}
	
	/**
	 * Returns the base path for the web server.
	 * @return Web server base path or empty string if not set.
	 */
	public String getWebserverBasePath() {
		String basePath = config.getObject("webserver").getString("basePath");
		return basePath != null ? basePath : "";
	}
	
	/**
	 * Returns the directory containing the static files to be delivered by the web server. 
	 * @return Path for static files or "/" of not set.
	 */
	public String getWebserverStaticDirectory() {
		String path = config.getObject("webserver").getString("statics");
		return path != null ? path : "/";
	}
	
	/**
	 * Returns the port of the web server.
	 * @return Web server port.
	 */
	public int getWebserverPort() {
		return config.getObject("webserver").getInteger("port");
	}
	
	/**
	 * Returns the configuration for a service.
	 * @param serviceId ID of the service of which the configuration should be returned.
	 * @return Service configuration of <code>null</code> if no configuration for the given ID is stored.
	 */
	public JsonObject getServiceConfiguration(String serviceId) {
		return config.getObject("services").getObject(serviceId);
	}
	
	/**
	 * Returns the configuration for the event bus bridge.
	 * @return Configuration of the event bus bridge.
	 */
	public JsonObject getEventBusBridgeConfiguration() {
		return config.getObject("eventBusBridge");
	}
	
	/**
	 * Checks if the debug mode is enabled.
	 * @return <code>true</code> of debug mode is enabled, otherwise <code>false</code>.
	 */
	public boolean isDebugModeEnabled() {
		return config.getBoolean("debugMode", false);
	}
	
	/**
	 * Returns the list of configured locations.
	 * @return List of locations. May be empty.
	 */
	public List<Location> getLocations() {
		return locations;
	}
	
	public JsonObject getStatusSingalConfiguration() {
		return config.getObject("statusSignal");
	}
	
	public JsonObject getClientConnectionConfig() {
		return config.getObject("clientConnection", new JsonObject());
	}
	
	public boolean sendSMSNotifications() {
		return config.getBoolean("sendSMSNotifications", false);
	}
}
