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
import com.skplanet.querycache.servlet.QCEventRequest;
import com.skplanet.querycache.servlet.QCEventResponse;

/**
 * Created by nazgul33 on 15. 2. 23.
 */
public class QCWebSocketClient implements WebSocketListener {

	private static final Logger LOG = LoggerFactory.getLogger(QCWebSocketClient.class);

	private WebSocketClient client;
	private Session session;
	private long errorCount = 0;
	private static final long MAX_ERROR_COUNT = 10;

	private Timer timer = new Timer(true);

	private QCServer listener;
	private String cluster;
	private String url;
	private URI uri;

	private Gson gson = new Gson();
	
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

		QCEventRequest req = new QCEventRequest();
		req.setRequest(QCEventRequest.REQ_SUBSCRIBE);
		req.setChannel(QCEventRequest.CHN_RUNNINGQUERIES);
		sendMessage(gson.toJson(req));
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

		QCEventResponse res = null;
		try {
			res = gson.fromJson(message, QCEventResponse.class);
		} catch (Exception e) {
			LOG.error("Gson exception :", message);
		}

		if (res == null || res.getMsgType() == null) {
			LOG.error("invalid event object received.", message);
			return;
		}

		switch (res.getMsgType()) {
		case QCEventResponse.ADD:
		case QCEventResponse.UPDATE:
			LOG.debug("new RT query {} {}", cluster, url);
			if (res.getQuery() != null) {
				listener.processWebSocketAddEvent(res.getQuery());
			}
			break;
		case QCEventResponse.REMOVE:
			LOG.debug("removing RT query {} {}", cluster, url);
			if (res.getQuery() != null) {
				listener.processWebSocketRemoveEvent(res.getQuery());
			}
			break;
		case QCEventResponse.RESULT:
			LOG.debug("result {} {} {} ",cluster, url, res.getResult());
			break;
		case QCEventResponse.PONG:
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
		QCEventRequest req = new QCEventRequest();
		req.setRequest(QCEventRequest.REQ_PING);
		sendMessage(gson.toJson(req));
	}
}
