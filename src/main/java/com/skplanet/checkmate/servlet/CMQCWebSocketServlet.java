package com.skplanet.checkmate.servlet;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * Created by nazgul33 on 15. 2. 13.
 */

public class CMQCWebSocketServlet extends WebSocketServlet {
	
	private static final long serialVersionUID = -1985362762429496022L;

	private static final int MAX_TEXT_MESSAGE_SIZE = 10*1024*1024;
	
	@Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(120*1000); // 2min
        factory.getPolicy().setMaxTextMessageSize(MAX_TEXT_MESSAGE_SIZE);
        factory.register(CMQCWebSocket.class);
    }
}