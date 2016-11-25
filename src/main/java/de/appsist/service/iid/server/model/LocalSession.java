package de.appsist.service.iid.server.model;

import java.util.*;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import de.appsist.service.auth.connector.model.User;
import de.appsist.service.auth.connector.model.View;

/**
 * Model for a user session object.
 * @author simon.schwantzer(at)im-c.de
 */
public class LocalSession {
	private final Map<String, ServiceCatalog> serviceCatalogs;
	private final User user;
	private final String id;
	private final List<View> views;

	public LocalSession(String id, List<View> views, User user) {
		this.serviceCatalogs = new LinkedHashMap<String, ServiceCatalog>();
		this.id = id;
		this.views = views;
		this.user = user;
	}
	
	public void addServiceCatalog(ServiceCatalog serviceCatalog) {
		this.serviceCatalogs.put(serviceCatalog.getId(), serviceCatalog);
	}
	
	public ServiceCatalog getServiceCatalog(String catalogId) {
		return serviceCatalogs.get(catalogId);
	}
	
	public Set<String> getServiceCatalogIds() {
		return serviceCatalogs.keySet();
	}
	
	public User getUser() {
		return user;
	}

	public String getId() {
		return id;
	}
	
	public JsonObject asJson() {
		JsonObject json = new JsonObject();
		json.putString("id", id);
		json.putObject("user", user.asJson());
		JsonArray catalogs = new JsonArray();
		for (ServiceCatalog catalog : serviceCatalogs.values()) {
			catalogs.addObject(catalog.asJson());
		}
		json.putArray("catalogs", catalogs);
		JsonArray views = new JsonArray();
		for (View view : this.views) {
			views.addObject(view.asJson());
		}
		json.putArray("views", views);
		
		return json;
	}
}
