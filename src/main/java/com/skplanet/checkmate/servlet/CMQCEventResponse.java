package com.skplanet.checkmate.servlet;

public class CMQCEventResponse {
    
	public static final String PONG = "pong";
	
	private String msgType;

	public String getMsgType() {
		return msgType;
	}

	public void setMsgType(String msgType) {
		this.msgType = msgType;
	}

}