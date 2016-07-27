package com.skplanet.checkmate.querycache.data;

public class ClusterServerSysStats {
	private String server;
	private ServerRuntimeInfo jvm;
	private ServerSystemInfo system;
	private ServerThreadInfo threads;

	public ClusterServerSysStats(){}
	
	public ClusterServerSysStats(String server, ServerSystemStats svrStats) {
		this.server = server;
		this.jvm = (svrStats!=null?svrStats.getJvm():new ServerRuntimeInfo());
		this.system = (svrStats!=null?svrStats.getSystem():new ServerSystemInfo());
		this.threads = (svrStats!=null?svrStats.getThreads():new ServerThreadInfo());
	}

	public String getServer() {
		return server;
	}
	public void setServer(String server) {
		this.server = server;
	}
	public ServerRuntimeInfo getJvm() {
		return jvm;
	}
	public void setJvm(ServerRuntimeInfo jvm) {
		this.jvm = jvm;
	}
	public ServerSystemInfo getSystem() {
		return system;
	}
	public void setSystem(ServerSystemInfo system) {
		this.system = system;
	}
	public ServerThreadInfo getThreads() {
		return threads;
	}
	public void setThreads(ServerThreadInfo threads) {
		this.threads = threads;
	}
}