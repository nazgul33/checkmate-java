package com.skplanet.checkmate.querycache;

import com.google.gson.Gson;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Request;

/**
 * Created by nazgul33 on 15. 2. 23.
 */
public class QCClientWebSocket implements WebSocketListener {
    private static final boolean DEBUG = false;
    private static final Logger LOG = LoggerFactory.getLogger("websocket-client-qc");

    private Session outbound = null;
    private QCServer server = null;

    private static class RequestMsg {
        public String request;
        public String channel;
        public String data;
    }

    public QCClientWebSocket(QCServer server) {
        this.server = server;
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len)
    {
        /* not interested */
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason)
    {
        LOG.info("WebSocket to " + server.name + " closed");
        this.outbound = null;
        server.removeRtWebSocket();
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.outbound = session;
        LOG.info("WebSocket to " + server.name + " established");

        RequestMsg req = new RequestMsg();
        req.request = "subscribe";
        req.channel = "runningQueries";

        Gson gson = new Gson();
        String msg = gson.toJson(req);

        sendMessage(msg);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        LOG.error("websocket error", cause);
        if (this.outbound == null) {
            // connection error. server's websocket should be cleared.
            server.removeRtWebSocket();
        }
    }

    public static final String evtStrQueryAdded = "runningQueryAdded";
    public static final String evtStrQueryUpdated = "runningQueryUpdated";
    public static final String evtStrQueryRemoved = "runningQueryRemoved";
    public static final String evtStrResult = "result";
    public static final String evtStrPong = "pong";
    private static class RunningQueryEvent {
        public String msgType = null;
        public String result = null;
        public QCQuery.QueryImport query = null;
        public String queryId = null;
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

        QCQuery q;
        switch (evt.msgType) {
            case evtStrQueryAdded:
            case evtStrQueryUpdated:
                if (evt.query != null) {
                    LOG.debug("new RT query");
                    q = new QCQuery(server, evt.query);
                    server.processAddQueryEvent(q);
                }
                break;
            case evtStrQueryRemoved:
                if (evt.query != null) {
                    LOG.debug("removing RT query");
                    q = new QCQuery(server, evt.query);
                    server.processRemoveQueryEvent(q);
                }
                break;
            case evtStrResult:
                LOG.info("result " + evt.result);
                break;
            case evtStrPong:
                LOG.debug("pong!!");
                break;
            default:
                LOG.warn("ignoring message", message);
                break;
        }
    }

    public void sendMessage(String message) {
        if (outbound == null || !outbound.isOpen())
            return;
        outbound.getRemote().sendString(message, null);
    }

    public void ping() {
        Gson gson = new Gson();
        RequestMsg msg = new RequestMsg();
        msg.request = "ping";
        sendMessage(gson.toJson(msg));
    }
}
