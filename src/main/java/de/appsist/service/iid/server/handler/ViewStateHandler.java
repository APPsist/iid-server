package de.appsist.service.iid.server.handler;

import de.appsist.service.auth.connector.model.View;

public interface ViewStateHandler {
	public void viewStateChanged(View view, ViewState state);
}
