package com.skplanet.checkmate.querycache;

public class QCClusterOptions {
	
	private int webPort;
	private long partialUpdateInterval;
	private long fullUpdateInterval;
	private int maxCompleteQueries;
	private boolean useWebSocket;
	private long updateFailSleepTime;
	
	public long getUpdateFailSleepTime() {
		return updateFailSleepTime;
	}

	public void setUpdateFailSleepTime(long updateFailSleepTime) {
		this.updateFailSleepTime = updateFailSleepTime;
	}

	private String[] servers;

	public int getWebPort() {
		return webPort;
	}
	
	public void setWebPort(int webPort) {
		this.webPort = webPort;
	}
	
	public long getPartialUpdateInterval() {
		return partialUpdateInterval;
	}
	
	public void setPartialUpdateInterval(long partialUpdateInterval) {
		this.partialUpdateInterval = partialUpdateInterval;
	}
	
	public long getFullUpdateInterval() {
		return fullUpdateInterval;
	}
	
	public void setFullUpdateInterval(long fullUpdateInterval) {
		this.fullUpdateInterval = fullUpdateInterval;
	}
	
	public String[] getServers() {
		return servers;
	}
	
	public void setServers(String[] servers) {
		this.servers = servers;
	}
	
	public int getMaxCompleteQueries() {
		return maxCompleteQueries;
	}
	
	public void setMaxCompleteQueries(int maxCompleteQueries) {
		this.maxCompleteQueries = maxCompleteQueries;
	}
	
	public boolean isUseWebSocket() {
		return useWebSocket;
	}
	
	public void setUseWebSocket(boolean useWebSocket) {
		this.useWebSocket = useWebSocket;
	}
}