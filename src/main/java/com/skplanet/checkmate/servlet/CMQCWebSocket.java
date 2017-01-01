package com.skplanet.checkmate.servlet;


import java.util.List;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.querycache.QCCluster;
import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.querycache.server.cli.QueryProfile;
import com.skplanet.querycache.servlet.QCWebSocket.ChangedProfile;

/**
 * Created by nazgul33 on 15. 2. 13.
 */
public class CMQCWebSocket implements WebSocketListener {

	private static final Logger LOG = LoggerFactory.getLogger(CMQCWebSocket.class);

	private String client;
	private Session session;
	private long idx;
	private QCClusterManager qcMgr = CheckMateServer.getClusterManager();
	private QCCluster cluster;
	private Gson gson = new Gson();

	private String okMessage;
	private String failMessage;
	private String pongMessage;
	private String unsupportMessage;
	private String errorMessage;
	
	public CMQCWebSocket() {
		okMessage = gson.toJson(new Response(Response.SUBSCRIBE, "OK"));
		failMessage = gson.toJson(new Response(Response.SUBSCRIBE, "FAIL"));
		pongMessage = gson.toJson(new Response(Response.PONG, "PONG"));
		unsupportMessage = gson.toJson(new Response(Response.UNSUPPORT, "unsupport"));
		errorMessage = gson.toJson(new Response(Response.UNSUPPORT, "message not json"));
	}
	
	public String getClient() {
		return client;
	}

	@Override
	public void onWebSocketBinary(byte[] payload, int offset, int len) {
		/* only interested in test messages */
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason) {
		if (session != null) {
			try {
				session.disconnect();
			} catch (Exception e) {
				LOG.error("ws disconnect error. client={}, idx={}, statusCode={}, reason={}", client, idx, statusCode, reason, e);
			}
			session = null;
		}
		if (cluster != null){
			cluster.removeSubscriber(idx);
		}
		LOG.info("ws closed. client={}, idx={}, status={}, reason={}", client, idx, statusCode, reason);
	}

	@Override
	public void onWebSocketConnect(Session session) {
		this.session = session;
		this.client = session.getRemoteAddress().toString();
		this.idx = qcMgr.getEventSubscriberIdx();
		LOG.info("ws connected. client={}, idx={}", client, idx);
	}

	@Override
	public void onWebSocketError(Throwable cause) {
		LOG.error("ws error", cause);
	}

	@Override
	public void onWebSocketText(String message) {
		if (session == null || !session.isOpen())
			return;

		try {
			Request req = gson.fromJson(message, Request.class);
	
			switch (req.getRequest()) {
			case Request.SUBSCRIBE: {
				if (Request.CLUSTER.equals(req.getChannel())) {
					cluster = qcMgr.getCluster(req.getData());
					if (cluster != null) {
						cluster.addSubscriber(idx, this);
						send(okMessage);
					} else {
						send(failMessage);
					}
				} else {
					send(failMessage);
				}
				break;
			}
			case Request.PING : {
				send(pongMessage);
				break;
			}
			default:
				send(unsupportMessage);
				break;
			}
		} catch (Exception e) {
			LOG.error("ws error={}, client={}, idx={}", e.getMessage(), client, idx);
			send(errorMessage);
		}
	}

	public void send(String message) {
		if (session !=null && session.isOpen() && idx > 0) {
			session.getRemote().sendString(message, null);	
		}
	}

	public static class Request {
	    
		public static final String SUBSCRIBE = "subscribe";
		public static final String PING = "ping";
		public static final String CLUSTER = "cluster";
		
		private String r;
	    private String c;
	    private String d;

	    public String getRequest() {
			return r;
		}
		public void setRequest(String request) {
			this.r = request;
		}
		public String getChannel() {
			return c;
		}
		public void setChannel(String channel) {
			this.c = channel;
		}
		public String getData() {
			return d;
		}
		public void setData(String data) {
			this.d = data;
		}
	}
	
	public static class Response {
	    
		public static final String SUBSCRIBE = "subscribe";
		public static final String PONG = "pong";
		public static final String UNSUPPORT = "unsupport";
		public static final String DATA = "data";
		
		private String t;
		private String d;
		private List<QueryProfile> q;
		private List<ChangedProfile> c;

		public Response(){}
		
		public Response(String type) {
			this.t = type;
		}
		
		public Response(String type, String data) {
			this.t = type;
			this.d = data;
		}
		
		public String getType() {
			return t;
		}

		public void setType(String type) {
			this.t = type;
		}

		public String getData() {
			return d;
		}

		public void setData(String data) {
			this.d = data;
		}

		public List<QueryProfile> getProfiles() {
			return q;
		}

		public void setProfiles(List<QueryProfile> profiles) {
			this.q = profiles;
		}

		public List<ChangedProfile> getCprofiles() {
			return c;
		}

		public void setCprofiles(List<ChangedProfile> cprofiles) {
			this.c = cprofiles;
		}
	}
}
