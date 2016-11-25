package de.appsist.service.iid.server.handler;

import org.vertx.java.core.AsyncResultHandler;

import de.appsist.service.auth.connector.model.View;
import de.appsist.service.iid.server.model.Activity;
import de.appsist.service.iid.server.model.AssistanceStep;
import de.appsist.service.iid.server.model.LearningObject;
import de.appsist.service.iid.server.model.LocalSession;
import de.appsist.service.iid.server.model.Location;
import de.appsist.service.iid.server.model.Notification;
import de.appsist.service.iid.server.model.Popup;
import de.appsist.service.iid.server.model.ServiceCatalog;
import de.appsist.service.iid.server.model.SiteOverview;
import de.appsist.service.iid.server.model.StationInfo;


public interface ViewHandler {
	/**
	 * Initializes the view handler.
	 */
	public void init();
	
	/**
	 * Perform action to destroy the view handler.
	 */
	public void destroy();
	
	/**
	 * Add a listener for view state changes.
	 * @param handler Listener to add.
	 */
	public void addViewStateHandler(ViewStateHandler handler);
	
	/**
	 * Removes a listener for view state changes.
	 * @param handler Listener to remove.
	 */
	public void removeViewStateListener(ViewStateHandler handler);
	
	/**
	 * Returns the session representing
	 * @return Session managed by this handler.
	 */
	public LocalSession getSession();
	
	/**
	 * Sends a notification to be displayed on the device.
	 * @param notification Notification to display.
	 * @param resultHandler Handler to check of the operation succeeded. May be <code>null</code>.
	 */
	public void showNotification(Notification notification, AsyncResultHandler<Void> resultHandler);
	
	/**
     * Sends a notification to be displayed on the device.
     * 
     * @param notification
     *            Notification to display.
     * @param resultHandler
     *            Handler to check of the operation succeeded. May be <code>null</code>.
     */
    public void purgeNotifications(AsyncResultHandler<Void> resultHandler);

    /**
     * Dismisses a notification.
     * 
     * @param notificationId
     *            Identifier of the notification to dismiss.
     * @param resultHandler
     *            Handler to check of the operation succeeded. May be <code>null</code>.
     */
	public void dismissNotification(String notificationId, AsyncResultHandler<Void> resultHandler);
	
	/**
	 * Sends a notification to update a service catalog.
	 * @param catalog Service catalog to update.
	 * @param resultHandler Handler to check of the operation succeeded. May be <code>null</code>.
	 */
	public void updateCatalog(ServiceCatalog catalog, AsyncResultHandler<Void> resultHandler);
	
	/**
	 * Display a assistance step.
	 * @param assistance Assistance information.
	 * @param resultHandler Handler to check of the operation succeeded. May be <code>null</code>.
	 */
	public void displayAssistance(AssistanceStep assistance, AsyncResultHandler<Void> resultHandler);
	
	/**
	 * Display a learning object.
	 * @param learningObject Learning object to display.
	 * @param resultHandler Handler to check of the operation succeeded. May be <code>null</code>.
	 */
	public void displayLearningObject(LearningObject learningObject, AsyncResultHandler<Void> resultHandler);

    /**
     * Display a site overview page.
     * 
     * @param siteOverview
     *            Site overview to display.
     * @param resultHandler
     *            Handler to check of the operation succeeded. May be <code>null</code>.
     */
	public void displaySiteOverview(SiteOverview siteOverview, AsyncResultHandler<Void> resultHandler);
	
	/**
	 * Display a station info page.
	 * @param stationInfo Station info page to display.
	 * @param resultHandler Handler to check of the operation succeeded. May be <code>null</code>.
	 */
	public void displayStationInfo(StationInfo stationInfo, AsyncResultHandler<Void> resultHandler);
	
	/**
	 * Release this view to display the main menu again.
	 * @param resultHandler Handler to check of the operation succeeded. May be <code>null</code>.
	 */
	public void releaseView(AsyncResultHandler<Void> resultHandler);
	
	/**
	 * Display a popup window.
	 * @param popup Popup window to display.
	 * @param resultHandler Handler to check of the operation succeeded. May be <code>null</code>.
	 */
	public void displayPopup(Popup popup, AsyncResultHandler<Void> resultHandler);
		
	/**
	 * Returns the view of the device, containing information about the device class and id.
	 * @return Device information.
	 */
	public View getView();
	
	/**
	 * Returns the latest known location of the client.
	 * @return Latest known location of the client.
	 */
	public Location getLastKnownLocation();
	
	/**
	 * Returns the current user activity.
	 * @return User activity.
	 */
	public Activity getUserActivity();
}
