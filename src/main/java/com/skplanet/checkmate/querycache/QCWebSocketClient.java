package com.skplanet.checkmate.querycache;

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
import com.skplanet.querycache.servlet.QCWebSocket;

/**
 * Created by nazgul33 on 15. 2. 23.
 */
public class QCWebSocketClient implements WebSocketListener {

	private static final Logger LOG = LoggerFactory.getLogger(QCWebSocketClient.class);

	private static final long MAX_ERROR_COUNT = 10;

	private WebSocketClient client;
	private Session session;
	private long errorCount = 0;

	private Timer timer = new Timer(true);

	private QCCluster listener;
	private String cluster;
	private String wsUrl;
	private URI uri;

	private String pingMessage;
	
	private Gson gson = new Gson();
	
	public QCWebSocketClient(QCServer server, String wsUrl) throws URISyntaxException {

		this.cluster = server.getCluster().getName();
		this.listener = server.getCluster();
		this.wsUrl = wsUrl;
		this.uri = new URI(wsUrl);

		client = new WebSocketClient();
		client.setDaemon(true);
		
		pingMessage = gson.toJson(new QCWebSocket.Request(QCWebSocket.PING));
	}

	public void start() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					if (session == null || !session.isOpen()) {
						if (!client.isStarted()) {
							client.start();
						}
						client.connect(QCWebSocketClient.this, uri, new ClientUpgradeRequest());
						errorCount = 0;
					}
				} catch (Exception e) {
					errorCount++;
					LOG.error("ws error {} {} {}", cluster, wsUrl, e.getMessage());
				}
				if (errorCount > MAX_ERROR_COUNT) {
					try {
						TimeUnit.MINUTES.sleep(5);
					} catch (InterruptedException e) {
					}
				}
			}
		}, 0, 5*1000);
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				sendMessage(pingMessage);
			}
		}, 0, 5*1000);
	}

	public void stop() {
		timer.cancel();
		try {
			if (session != null && session.isOpen()) {
				session.close();
			}
		} catch (Exception e) {
			LOG.error("ws session error {} {} {}", cluster, wsUrl, e.getMessage());
		}
		try {
			if (client != null) {
				client.stop();
			}
		} catch (Exception e) {
			LOG.error("ws client stop failed. {} {}", cluster, wsUrl, e);
		}
	}

	@Override
	public void onWebSocketBinary(byte[] payload, int offset, int len) {
		/* not interested */
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason) {
		try {
			session.disconnect();
		} catch (Exception e) {
			LOG.error("ws close statusCode={}, reason={}, cluster={}, url={}",
					statusCode, reason, cluster, wsUrl);
		}
		session = null;
	}

	@Override
	public void onWebSocketConnect(Session session) {
		this.session = session;
	}

	@Override
	public void onWebSocketError(Throwable e) {
		LOG.error("ws error={}, cluster={}, url={}", e.getMessage(), cluster, wsUrl);
	}

	@Override
	public void onWebSocketText(String message) {

		LOG.info(message);
		try {
			QCWebSocket.Response resp = gson.fromJson(message, QCWebSocket.Response.class);
			switch(resp.getType()) {
			case QCWebSocket.OK:
				listener.processWebSocketEvent(resp.getProfiles(), resp.getCprofiles());
				break;
			case QCWebSocket.PONG:
				break;
			case QCWebSocket.FAIL:
			case QCWebSocket.ERROR :
				LOG.error(resp.getMsg());
				break;
			default:
				LOG.error("unsupported response type={}, cluster={}, url={}", resp.getType(), cluster, wsUrl);
			}
		} catch (Exception e){
			LOG.error("ws error={}, cluster={}, url={}", e.getMessage(), cluster, wsUrl);
		}
	}

	private void sendMessage(String message) {
		if (session != null && session.isOpen()) {
			session.getRemote().sendString(message, null);
		}
	}
}
