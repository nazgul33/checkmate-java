package com.skplanet.checkmate.querycache.data;

import com.skplanet.querycache.servlet.QCApiJVMRuntimeInfo;
import com.skplanet.querycache.servlet.QCApiSystemInfo;
import com.skplanet.querycache.servlet.QCApiThreadInfo;
import com.skplanet.querycache.servlet.QCApiServerSystemStats;

public class ClusterServerSysStats {
	private String server;
	private QCApiJVMRuntimeInfo jvm;
	private QCApiSystemInfo system;
	private QCApiThreadInfo threads;

	public ClusterServerSysStats(){}
	
	public ClusterServerSysStats(String server, QCApiServerSystemStats svrStats) {
		this.server = server;
		this.jvm = (svrStats!=null?svrStats.getJvm():new QCApiJVMRuntimeInfo());
		this.system = (svrStats!=null?svrStats.getSystem():new QCApiSystemInfo());
		this.threads = (svrStats!=null?svrStats.getThreads():new QCApiThreadInfo());
	}

	public String getServer() {
		return server;
	}
	public void setServer(String server) {
		this.server = server;
	}
	public QCApiJVMRuntimeInfo getJvm() {
		return jvm;
	}
	public void setJvm(QCApiJVMRuntimeInfo jvm) {
		this.jvm = jvm;
	}
	public QCApiSystemInfo getSystem() {
		return system;
	}
	public void setSystem(QCApiSystemInfo system) {
		this.system = system;
	}
	public QCApiThreadInfo getThreads() {
		return threads;
	}
	public void setThreads(QCApiThreadInfo threads) {
		this.threads = threads;
	}
}