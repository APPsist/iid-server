package de.appsist.service.iid.server.handler;

import java.util.*;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;

import de.appsist.service.auth.connector.model.*;
import de.appsist.service.iid.server.ConnectorRegistry;
import de.appsist.service.iid.server.HandlerRegistry;
import de.appsist.service.iid.server.model.LocalSession;

public class LocalSessionHandler {
	private final Map<String, LocalSession> sessions;
	// private final HandlerRegistry handlers;
	private final ConnectorRegistry connectors;
	
	public LocalSessionHandler(ConnectorRegistry connectors, HandlerRegistry handlers) {
		// this.handlers = handlers;
		this.connectors = connectors;
		this.sessions = new HashMap<>();
	}
	
	private void createSession(final User user, String token, View view, final AsyncResultHandler<LocalSession> resultHandler) {
		final Session session = new Session(UUID.randomUUID().toString());
		session.registerView(view);
		session.setUserId(user.getId());
		
		connectors.authService().storeSession(session, token, new AsyncResultHandler<Void>() {
			
			@Override
			public void handle(final AsyncResult<Void> storeRequest) {
				final LocalSession localSession = new LocalSession(session.getId(), session.getViews(), user);
				if (storeRequest.succeeded()) {
					sessions.put(session.getId(), localSession);
				}
				resultHandler.handle(new AsyncResult<LocalSession>() {
					
					@Override
					public boolean succeeded() {
						return storeRequest.succeeded();
					}
					
					@Override
					public LocalSession result() {
						return succeeded() ?  localSession : null;
					}
					
					@Override
					public boolean failed() {
						return !succeeded();
					}
					
					@Override
                    public Throwable cause()
                    {
						return storeRequest.cause();
					}
                });
            }
		});
	}
	
	public void registerView(final User user, final String token, final View view, final AsyncResultHandler<LocalSession> resultHandler) {
		connectors.authService().getSessionForUser(user.getId(), token, new AsyncResultHandler<de.appsist.service.auth.connector.model.Session>() {
			
			@Override
			public void handle(AsyncResult<de.appsist.service.auth.connector.model.Session> sessionRequest) {
				if (sessionRequest.succeeded()) {
					connectors.authService().registerView(sessionRequest.result().getId(), token, view, new AsyncResultHandler<de.appsist.service.auth.connector.model.Session>() {
						
						@Override
						public void handle(final AsyncResult<de.appsist.service.auth.connector.model.Session> registerRequest) {
							final LocalSession localSession;
							if (registerRequest.succeeded()) {
								Session session = registerRequest.result();
								localSession = new LocalSession(session.getId(), session.getViews(), user);
								sessions.put(localSession.getId(), localSession);
							} else {
								localSession = null;
							}
							resultHandler.handle(new AsyncResult<LocalSession>() {
								
								@Override
								public boolean succeeded() {
									return registerRequest.succeeded();
								}
								
								@Override
								public LocalSession result() {
									return succeeded() ? localSession : null;
								}
								
								@Override
								public boolean failed() {
									return !succeeded();
								}
								
								@Override
								public Throwable cause() {
									return registerRequest.cause();
								}
							});
						}
					});
				} else {
					createSession(user, token, view, resultHandler);
				}
			}
		});
	}
	
	public void removeView(final String sessionId, String token, final String viewId, final AsyncResultHandler<Void> resultHandler) {
		connectors.authService().removeView(sessionId, token, viewId, new AsyncResultHandler<Session>() {
			
			@Override
			public void handle(final AsyncResult<Session> removeRequest) {
				if (removeRequest.succeeded()) {
					Session session = removeRequest.result();
					User user = sessions.get(sessionId).getUser();
					LocalSession localSession = new LocalSession(session.getId(), session.getViews(), user);
					sessions.put(session.getId(), localSession);
				}
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
		
	}
	
	public LocalSession getSession(String sessionId) {
		return sessions.get(sessionId);
	}
	
	public boolean hasSession(String sessionId) {
		return sessions.containsKey(sessionId);
	}
}
