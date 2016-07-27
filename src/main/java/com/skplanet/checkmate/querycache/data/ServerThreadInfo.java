package com.skplanet.checkmate.querycache.data;

public class ServerThreadInfo {
	private int webServerThreads;
	private int webServerThreadsIdle;
	private int handlerThreads;
	private int handlerThreadsIdle;
	private int totalThreads;
	
	public int getWebServerThreads() {
		return webServerThreads;
	}
	public void setWebServerThreads(int webServerThreads) {
		this.webServerThreads = webServerThreads;
	}
	public int getWebServerThreadsIdle() {
		return webServerThreadsIdle;
	}
	public void setWebServerThreadsIdle(int webServerThreadsIdle) {
		this.webServerThreadsIdle = webServerThreadsIdle;
	}
	public int getHandlerThreads() {
		return handlerThreads;
	}
	public void setHandlerThreads(int handlerThreads) {
		this.handlerThreads = handlerThreads;
	}
	public int getHandlerThreadsIdle() {
		return handlerThreadsIdle;
	}
	public void setHandlerThreadsIdle(int handlerThreadsIdle) {
		this.handlerThreadsIdle = handlerThreadsIdle;
	}
	public int getTotalThreads() {
		return totalThreads;
	}
	public void setTotalThreads(int totalThreads) {
		this.totalThreads = totalThreads;
	}
}

