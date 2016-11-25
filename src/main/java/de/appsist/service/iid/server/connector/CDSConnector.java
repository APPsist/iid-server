package de.appsist.service.iid.server.connector;

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.json.JsonObject;

/**
 * Connector for the content delivery service.
 * @author simon.schwantzer(at)im-c.de
 */
public class CDSConnector {
	final HttpClient cdsClient;
	final String basePath;
	
	public CDSConnector(Vertx vertx, JsonObject httpConfig) {
		cdsClient = vertx.createHttpClient();
		cdsClient.setHost(httpConfig.getString("host", "localhost"));
		cdsClient.setPort(httpConfig.getInteger("port", 8080));
		cdsClient.setSSL(httpConfig.getBoolean("secure", false));
		basePath = httpConfig.getString("path");
	}
	
	/**
	 * Requests the manifest of a content package.
	 * @param contentId ID of the content package.
	 * @param resultHandler Handler for the asynchronous request.
	 */
	public void retrieveContentManifest(String contentId, AsyncResultHandler<JsonObject> resultHandler) {
		String path = basePath + "/" + contentId + "/content.json";
        cdsClient.get(path, new JsonResponseHandler(resultHandler)).setTimeout(10000).end();
	}
	
	public String getUrlForFile(String contentId, String fileName) {
		return basePath + "/" + contentId + "/" + fileName;
	}
}
