package com.skplanet.checkmate.servlet;

public class CMQCEventRequest {
    
	public static final String REQ_SUBSCRIBE = "subscribe";
	public static final String REQ_PING = "ping";
	public static final String CHN_CLUSTER = "cluster";
	
	private String request;
    private String channel;
    private String data;

    public String getRequest() {
		return request;
	}
	public void setRequest(String request) {
		this.request = request;
	}
	public String getChannel() {
		return channel;
	}
	public void setChannel(String channel) {
		this.channel = channel;
	}
	public String getData() {
		return data;
	}
	public void setData(String data) {
		this.data = data;
	}
}