package de.appsist.service.iid.server;

import java.util.*;
import java.util.Map.Entry;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;

public class ValueAggregationHandler<T, E> {
	private final AsyncResultHandler<Map<T, AsyncResult<E>>> finalResultHandler;
	private final List<AsyncResultHandler<E>> openRequests;
	private final Map<T, AsyncResultHandler<E>> resultHandlers;
	private boolean isAborted;
	private final Map<T, AsyncResult<E>> results;
	
	public ValueAggregationHandler(Collection<T> requesters, AsyncResultHandler<Map<T, AsyncResult<E>>> finalResultHandler) {
		this.finalResultHandler = finalResultHandler;
		openRequests = new ArrayList<>();
		isAborted = false;
		resultHandlers = new HashMap<>();
		results = new LinkedHashMap<>();
		for (final T requester : requesters) {
			
			AsyncResultHandler<E> resultHandler = new AsyncResultHandler<E>() {

				@Override
				public void handle(AsyncResult<E> result) {
					openRequests.remove(this);
					checkAndComplete(requester, result);
				}
			};
			resultHandlers.put(requester, resultHandler);
			openRequests.add(resultHandler);
		}
	}
	
	public AsyncResultHandler<E> getRequestHandler(T requester) {
		return resultHandlers.get(requester);
	}
	
	
	private void checkAndComplete(final T requester, final AsyncResult<E> result) {
		if (isAborted) return; // We already threw an error.
		results.put(requester, result);

		if (openRequests.isEmpty()) {
			finalResultHandler.handle(new AsyncResult<Map<T,AsyncResult<E>>>() {
				
				@Override
				public boolean succeeded() {
					for (AsyncResult<E> result : results.values()) {
						if (result.failed()) return false;
					}
					return true;
                }
				
				@Override
				public Map<T,AsyncResult<E>> result() {
					return results;
				}
				
				@Override
				public boolean failed() {
					return !succeeded();
				}
				
				@Override
				public Throwable cause() {
					List<String> errors = new ArrayList<String>();
					for (Entry<T, AsyncResult<E>> entry : results.entrySet()) {
						if (entry.getValue().failed()) {
							errors.add(entry.getValue().toString());
						}
					}
					return errors.size() > 0 ? new Exception("The operation failed for the following entries: " + errors.toString()) : null;
				}
			});
		}
	}
	
	public void abort(final Throwable reason) {
		if (!isAborted) {
			isAborted = true;
			finalResultHandler.handle(new AsyncResult<Map<T,AsyncResult<E>>>() {

				@Override
				public Map<T, AsyncResult<E>> result() {
					return results;
				}

				@Override
				public Throwable cause() {
					return reason;
				}

				@Override
				public boolean succeeded() {
					return false;
				}

				@Override
				public boolean failed() {
					return true;
				}
			});
		}
	}
	
	
}