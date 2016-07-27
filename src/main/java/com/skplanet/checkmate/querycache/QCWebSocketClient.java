package com.skplanet.checkmate.querycache;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.skplanet.checkmate.querycache.data.QCQueryImport;

/**
 * Created by nazgul33 on 15. 2. 23.
 */
public class QCWebSocketClient implements WebSocketListener {

	private static final Logger LOG = LoggerFactory.getLogger(QCWebSocketClient.class);

	public static final String EVENT_RUN_QUERY_ADDED = "runningQueryAdded";
	public static final String EVENT_RUN_QUERY_UPDATED = "runningQueryUpdated";
	public static final String EVENT_RUN_QUERY_REMOVED = "runningQueryRemoved";
	public static final String EVENT_RESULT = "result";
	public static final String EVENT_PONG = "pong";

	private WebSocketClient client;
	private Session session;
	private long errorCount = 0;
	private static final long MAX_ERROR_COUNT = 10;

	private Timer timer = new Timer(true);

	private QCServer listener;
	private String cluster;
	private String url;
	private URI uri;

	public QCWebSocketClient(final QCServer server, final String url) throws URISyntaxException {

		this.cluster = server.getCluster().getName();
		this.listener = server;
		this.url = url;
		this.uri = new URI(url);

		client = new WebSocketClient();
		client.setDaemon(true);
	}

	public void start() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					if (session == null || !session.isOpen()) {
						connect();
					}
				} catch (Exception e) {
					errorCount++;
					LOG.error("websocket error {} {} {}", 
							listener.getCluster().getName(), 
							url, 
							e.getMessage());
				}
				if (errorCount > MAX_ERROR_COUNT) {
					try {
						TimeUnit.MINUTES.sleep(1);
					} catch (InterruptedException e) {
					}
				}
			}
		}, 0, 5*1000);
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				ping();
			}
		}, 0, 5*1000);
	}

	public void stop() {
		timer.cancel();
		if (session != null) {
			session.close();
		}
		if (client != null) {
			try {
				client.stop();
			} catch (Exception e) {
				LOG.error("webclient stop failed. {} {}", cluster, url, e);
			}
		}
	}

	private void connect() throws Exception {
		try {
			if (!client.isStarted()) {
				client.start();
			}
			ClientUpgradeRequest req = new ClientUpgradeRequest();
			client.connect(this, uri, req);
			errorCount = 0;
		} catch (Exception e) {
			throw e;
		}
	}

	@Override
	public void onWebSocketBinary(byte[] payload, int offset, int len) {
		/* not interested */
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason) {
		LOG.info("websocket closed {} {} statusCode={}, reason={}", 
				cluster, url, statusCode, reason);
		try {
			session.disconnect();
		} catch (IOException e) {
			LOG.error("websocket session error {} {} {}", cluster, url, e.getMessage());
		}
	}

	@Override
	public void onWebSocketConnect(Session session) {

		this.session = session;
		LOG.info("websocket connected {} {}", cluster, url);

		RequestMsg req = new RequestMsg();
		req.request = "subscribe";
		req.channel = "runningQueries";

		Gson gson = new Gson();
		String msg = gson.toJson(req);

		sendMessage(msg);
	}

	@Override
	public void onWebSocketError(Throwable cause) {
		if (cause instanceof ConnectException) {
			LOG.error("websocket error {} {} {}", cluster, url, cause.getMessage());
		} else {
			LOG.error("websocket error {} {}", cluster, url, cause);
		}
		session = null;
	}

	@Override
	public void onWebSocketText(String message) {
		Gson gson = new Gson();
		RunningQueryEvent evt = null;
		try {
			evt = gson.fromJson(message, RunningQueryEvent.class);
		} catch (Exception e) {
			LOG.error("Gson exception :", message);
		}

		if (evt == null || evt.msgType == null) {
			LOG.error("invalid event object received.", message);
			return;
		}

		switch (evt.msgType) {
		case EVENT_RUN_QUERY_ADDED:
		case EVENT_RUN_QUERY_UPDATED:
			LOG.debug("new RT query {} {}", cluster, url);
			if (evt.query != null) {
				listener.processWebSocketAddEvent(evt.query);
			}
			break;
		case EVENT_RUN_QUERY_REMOVED:
			LOG.debug("removing RT query {} {}", cluster, url);
			if (evt.query != null) {
				listener.processWebSocketRemoveEvent(evt.query);
			}
			break;
		case EVENT_RESULT:
			LOG.debug("result {} {} {} ",cluster, url, evt.result);
			break;
		case EVENT_PONG:
			LOG.debug("pong {} {}", cluster, url);
			break;
		default:
			LOG.warn("ignoring message", message);
			break;
		}
	}

	private void sendMessage(String message) {
		if (session != null && session.isOpen()) {
			session.getRemote().sendString(message, null);
		}
	}

	private void ping() {
		LOG.debug("ping {} {}", cluster, url);
		Gson gson = new Gson();
		RequestMsg msg = new RequestMsg();
		msg.request = "ping";
		sendMessage(gson.toJson(msg));
	}

	private static class RequestMsg {
		public String request;
		public String channel;
		public String data;
	}

	private static class RunningQueryEvent {
		public String msgType = null;
		public String result = null;
		public QCQueryImport query = null;
		public String queryId = null;
	}
}
