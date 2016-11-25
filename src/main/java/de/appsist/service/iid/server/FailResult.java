package de.appsist.service.iid.server;


import org.vertx.java.core.AsyncResult;

public class FailResult implements AsyncResult<Void> {
	private final Throwable cause;

	public FailResult(Throwable cause) {
		this.cause = cause;
	}

	@Override
	public Void result() {
		return null;
	}

	@Override
	public Throwable cause() {
		return cause;
	}

	@Override
	public boolean succeeded() {
		return false;
	}

	@Override
	public boolean failed() {
		return true;
	}
}