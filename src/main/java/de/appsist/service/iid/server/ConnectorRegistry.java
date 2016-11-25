package de.appsist.service.iid.server;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.json.JsonObject;

import de.appsist.service.auth.connector.AuthServiceConnector;
import de.appsist.service.iid.server.connector.CDSConnector;
import de.appsist.service.sms.connector.SMSGatewayConnector;

public class ConnectorRegistry {
	private final Vertx vertx;
	
	private AuthServiceConnector authServiceConnector;
	private CDSConnector cdsConnector;
	private SMSGatewayConnector smsConnector;
	
	public ConnectorRegistry(Vertx vertx) {
		this.vertx = vertx;
	}
	
	/**
	 * Initializes the auth service connector.
	 * @param address Address of the auth service.
	 */
	public void initAuthService(String address) {
		this.authServiceConnector = new AuthServiceConnector(vertx.eventBus(), address);
	}
	
	/**
	 * Returns the auth service connector.
	 * @return Auth service connector or <code>null</code> if not initialized.
	 */
	public AuthServiceConnector authService() {
		return authServiceConnector;
	}
	
	/**
	 * Initializes the content deliver service connector.
	 * @param httpEndpoint HTTP endpoint configuration to access the service. 
	 */
	public void initCDSConnector(JsonObject httpEndpoint) {
		this.cdsConnector = new CDSConnector(vertx, httpEndpoint);
	}
	
	/**
	 * Returns the content deliver service connector.
	 * @return Content deliver service connector or <code>null</code> if not initialized.
	 */
    public CDSConnector cdsConnector() {
		return cdsConnector;
    }
    
    /**
     * Initializes the SMS gatway connector.
     * @param address Address of the SMS gateway service.
     */
    public void initSMSConnector(String address) {
    	this.smsConnector = new SMSGatewayConnector(vertx.eventBus(), address);
    }
    
    /**
     * Returns the SMS gateway connector.
     * @return SMS gateway connector or <code>null</code> if not initialized. 
     */
    public SMSGatewayConnector smsConnector() {
    	return smsConnector;
    }

}
