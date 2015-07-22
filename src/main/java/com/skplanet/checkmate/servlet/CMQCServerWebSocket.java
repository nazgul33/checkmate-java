package com.skplanet.checkmate.servlet;


import com.google.gson.Gson;
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
    private static final boolean DEBUG = false;
    private static final Logger LOG = LoggerFactory.getLogger("websocket-server-qc");

    private Session outbound;
    private int id = -1;
    private QCClusterManager qcMgr = QCClusterManager.getInstance();
    private QCCluster cluster = null;

    private static class RequestMsg {
        public String request;
        public String channel;
        public String data;
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len)
    {
        /* only interested in test messages */
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason)
    {
        this.outbound = null;
        if ( this.id >= 0 && this.cluster != null) {
            cluster.unSubscribe(id);
        }
        this.id = -1;
        this.cluster = null;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.outbound = session;
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        LOG.error("websocket error", cause);
    }

    @Override
    public void onWebSocketText(String message) {
        if (outbound == null || !outbound.isOpen())
            return;

        Gson gson = new Gson();
        RequestMsg msg = gson.fromJson(message, RequestMsg.class);
        switch (msg.request) {
            case "subscribe": {
                if ("cluster".equals(msg.channel)) {
                    this.cluster = qcMgr.getCluster(msg.data);
                    if (this.cluster != null) {
                        this.id = this.cluster.subscribe(this);
                    }
                }
                return;
            }
            case "ping": {
                outbound.getRemote().sendString("{\"msgType\":\"pong\"}", null);
                return;
            }
        }

        LOG.error("unknown message " + message);
    }

    public void sendMessage(String message) {
        if (outbound == null || !outbound.isOpen())
            return;
        outbound.getRemote().sendString(message, null);
    }
}
