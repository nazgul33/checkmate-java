package com.skplanet.checkmate.querycache.data;

public class ServerSystemInfo {
	private double loadSystem;
	private double loadProcess;
	private long memPhysFree;
	private long memPhysTotal;
	private long swapFree;
	private long swapTotal;

	public double getLoadSystem() {
		return loadSystem;
	}
	public void setLoadSystem(double loadSystem) {
		this.loadSystem = loadSystem;
	}
	public double getLoadProcess() {
		return loadProcess;
	}
	public void setLoadProcess(double loadProcess) {
		this.loadProcess = loadProcess;
	}
	public long getMemPhysFree() {
		return memPhysFree;
	}
	public void setMemPhysFree(long memPhysFree) {
		this.memPhysFree = memPhysFree;
	}
	public long getMemPhysTotal() {
		return memPhysTotal;
	}
	public void setMemPhysTotal(long memPhysTotal) {
		this.memPhysTotal = memPhysTotal;
	}
	public long getSwapFree() {
		return swapFree;
	}
	public void setSwapFree(long swapFree) {
		this.swapFree = swapFree;
	}
	public long getSwapTotal() {
		return swapTotal;
	}
	public void setSwapTotal(long swapTotal) {
		this.swapTotal = swapTotal;
	}
}