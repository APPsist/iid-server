package de.appsist.service.iid.server.handler;

import java.util.Set;

import de.appsist.service.auth.connector.model.User;
import de.appsist.service.iid.server.handler.ViewHandler;

/**
 * Generic interface for client handlers.
 * @author simon.schwantzer(at)im-c.de
 *
 */
public interface ClientHandler {
	/**
	 * Returns all client connections for a specific usersession.
	 * @param sessionId Identifier of a user session.
	 * @return Map with view IDs as keys and client connections as values. May be empty.
	 */
	public Set<? extends ViewHandler> getViewHandlersForSession(String sessionId);
	
	/**
	 * Returns a specific connection.
	 * @param sessionId Session identifier.
	 * @param viewId Identifier of the view to return the connection of.
	 * @return Connection or <code>null</code> if no session with the requested view exists.
	 */
	public ViewHandler getViewHandler(String sessionId, String viewId);
	
	/**
	 * Returns the user of a session.
	 * @return User of the session or <code>null</code> if the requested session does not exist.
	 */
	public User getUserForSession(String sessionId);

}
