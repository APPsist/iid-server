package de.appsist.service.iid.server;

import java.util.*;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;

/**
 * 
 * @author simon.schwantzer(at)im-c.de
 *
 * @param <T> Key class for the result handlers.
 */
public class ResultAggregationHandler<T> {
	private final AsyncResultHandler<Void> finalResultHandler;
	private final Set<AsyncResultHandler<Void>> openRequests;
	private final Map<T, AsyncResultHandler<Void>> resultHandlers;
	private boolean isAborted;
	
	public ResultAggregationHandler(Set<T> requesters, AsyncResultHandler<Void> finalResultHandler) {
		this.finalResultHandler = finalResultHandler;
		openRequests = new HashSet<>();
		isAborted = false;
		resultHandlers = new HashMap<>();
		for (T requester : requesters) {
			AsyncResultHandler<Void> resultHandler = new AsyncResultHandler<Void>() {

				@Override
				public void handle(AsyncResult<Void> result) {
					openRequests.remove(this);
					checkAndComplete(result);
				}
			};
			resultHandlers.put(requester, resultHandler);
			openRequests.add(resultHandler);
		}
	}
	
	public AsyncResultHandler<Void> getRequestHandler(T requester) {
		return resultHandlers.get(requester);
	}
	
	
	private void checkAndComplete(final AsyncResult<Void> result) {
		if (isAborted) return; // We already threw an error.
		if (openRequests.isEmpty() && result.succeeded()) {
			finalResultHandler.handle(new AsyncResult<Void>() {
				
				@Override
				public boolean succeeded() {
                    return true;
				}
				
				@Override
				public Void result() {
					return null;
				}
				
				@Override
				public boolean failed() {
					return false;
				}
				
				@Override
				public Throwable cause() {
					return null;
				}
			});
		} else {
			if (result.failed()) {
				isAborted = true; // We are done here.
				finalResultHandler.handle(new AsyncResult<Void>() {
					
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
						return result.cause();
					}
				});
			}
		}
	}
	
	public void abort(final Throwable reason) {
		if (!isAborted) {
			isAborted = true;
			finalResultHandler.handle(new AsyncResult<Void>() {
				
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
					return reason;
				}
			});
		}
	}
	
	
}