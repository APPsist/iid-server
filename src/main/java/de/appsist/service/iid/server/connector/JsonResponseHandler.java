package de.appsist.service.iid.server.connector;

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;

public class JsonResponseHandler implements Handler<HttpClientResponse> {
	private final AsyncResultHandler<JsonObject> resultHandler;
	
	public JsonResponseHandler(AsyncResultHandler<JsonObject> resultHandler) {
		this.resultHandler = resultHandler;
	}
	
	@Override
	public void handle(final HttpClientResponse response) {
			response.bodyHandler(new Handler<Buffer>() {
			
			@Override
			public void handle(Buffer buffer) {
				final String body = buffer.toString();
				resultHandler.handle(new AsyncResult<JsonObject>() {
					
					@Override
					public boolean succeeded() {
						return response.statusCode() == 200;
					}
					
					@Override
					public JsonObject result() {
						return succeeded() ? new JsonObject(body) : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
					public Throwable cause() {
						return failed() ? new HttpException(body, response.statusCode()) : null;
					}
				});
				
			}
		});
	}
}