package com.skplanet.checkmate.servlet;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import javax.servlet.annotation.WebServlet;

/**
 * Created by nazgul33 on 15. 2. 13.
 */

@WebServlet(name = "CheckMate WebSocket for QueryCache", urlPatterns = { "/api/qc/websocket" })
public class CMWebSocketServletQC extends WebSocketServlet {
    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(120*1000); // 2min
        factory.register(CMWebSocketQC.class);
    }
}
