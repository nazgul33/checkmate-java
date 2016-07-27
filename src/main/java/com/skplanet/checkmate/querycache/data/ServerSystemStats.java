package com.skplanet.checkmate.querycache.data;

public class ServerSystemStats {
	
	private ServerRuntimeInfo jvm;
	private ServerSystemInfo system;
	private ServerThreadInfo threads;
	
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