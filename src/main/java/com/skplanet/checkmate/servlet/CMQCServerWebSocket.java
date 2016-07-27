package com.skplanet.checkmate.servlet;


import com.google.gson.Gson;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.querycache.QCCluster;
import com.skplanet.checkmate.querycache.QCClusterManager;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by nazgul33 on 15. 2. 13.
 */
public class CMQCServerWebSocket implements WebSocketListener {
    
    private static final Logger LOG = LoggerFactory.getLogger("websocket-server-qc");

    private Session session;
    private int id = -1;
    private QCClusterManager qcMgr = CheckMateServer.getClusterManager();
    private QCCluster cluster = null;

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        /* only interested in test messages */
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
    	session.close();
        if ( id >= 0 && cluster != null) {
            cluster.unsubscribe(id);
        }
        id = -1;
        cluster = null;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session;
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        LOG.error("websocket error", cause);
    }

    @Override
    public void onWebSocketText(String message) {
        if (session == null || !session.isOpen())
            return;

        Gson gson = new Gson();
        RequestMsg msg = gson.fromJson(message, RequestMsg.class);
        switch (msg.request) {
            case "subscribe": {
                if ("cluster".equals(msg.channel)) {
                    cluster = qcMgr.getCluster(msg.data);
                    if (cluster != null) {
                        id = cluster.subscribe(this);
                    }
                }
                return;
            }
            case "ping": {
                sendMessage("{\"msgType\":\"pong\"}");
                return;
            }
        }

        LOG.error("unknown message " + message);
    }

    public void sendMessage(String message) {
        if (session == null || !session.isOpen())
            return;
        session.getRemote().sendString(message, null);
    }
    
    private static class RequestMsg {
        public String request;
        public String channel;
        public String data;
    }
}
