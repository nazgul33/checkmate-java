package com.skplanet.checkmate.querycache;

public class QCClusterOptions {
	private int webPort = 8080;
	private int partialUpdateInterval = 3000;
	private int fullUpdateInterval = 10000;
	private int serverMaxCompleteQueries = 100;
	
	private String[] servers;

	public int getWebPort() {
		return webPort;
	}
	public void setWebPort(int webPort) {
		this.webPort = webPort;
	}
	public int getPartialUpdateInterval() {
		return partialUpdateInterval;
	}
	public void setPartialUpdateInterval(int partialUpdateInterval) {
		this.partialUpdateInterval = partialUpdateInterval;
	}
	public int getFullUpdateInterval() {
		return fullUpdateInterval;
	}
	public void setFullUpdateInterval(int fullUpdateInterval) {
		this.fullUpdateInterval = fullUpdateInterval;
	}
	public String[] getServers() {
		return servers;
	}
	public void setServers(String[] servers) {
		this.servers = servers;
	}
	public int getServerMaxCompleteQueries() {
		return serverMaxCompleteQueries;
	}
	public void setServerMaxCompleteQueries(int serverMaxCompleteQueries) {
		this.serverMaxCompleteQueries = serverMaxCompleteQueries;
	}
}