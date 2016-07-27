package com.skplanet.checkmate.querycache.data;

public class ServerRuntimeInfo {
	private int nProcessors;
	private long memFree;
	private long memTotal;
	private long memMax;
	
	public int getnProcessors() {
		return nProcessors;
	}
	public void setnProcessors(int nProcessors) {
		this.nProcessors = nProcessors;
	}
	public long getMemFree() {
		return memFree;
	}
	public void setMemFree(long memFree) {
		this.memFree = memFree;
	}
	public long getMemTotal() {
		return memTotal;
	}
	public void setMemTotal(long memTotal) {
		this.memTotal = memTotal;
	}
	public long getMemMax() {
		return memMax;
	}
	public void setMemMax(long memMax) {
		this.memMax = memMax;
	}
}