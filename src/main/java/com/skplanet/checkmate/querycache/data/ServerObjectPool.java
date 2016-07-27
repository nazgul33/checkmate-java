package com.skplanet.checkmate.querycache.data;

public class ServerObjectPool {
	// configuration
	private long sReloadCycle = 0;
	private int  sMaxPoolSize = 0;
	private float sReloadingThreshold = 0;
	private int sCellCoeff = 0;
	private int[] poolSize = {0, 0, 0, 0};

	public long getsReloadCycle() {
		return sReloadCycle;
	}
	public void setsReloadCycle(long sReloadCycle) {
		this.sReloadCycle = sReloadCycle;
	}
	public int getsMaxPoolSize() {
		return sMaxPoolSize;
	}
	public void setsMaxPoolSize(int sMaxPoolSize) {
		this.sMaxPoolSize = sMaxPoolSize;
	}
	public float getsReloadingThreshold() {
		return sReloadingThreshold;
	}
	public void setsReloadingThreshold(float sReloadingThreshold) {
		this.sReloadingThreshold = sReloadingThreshold;
	}
	public int getsCellCoeff() {
		return sCellCoeff;
	}
	public void setsCellCoeff(int sCellCoeff) {
		this.sCellCoeff = sCellCoeff;
	}
	public int[] getPoolSize() {
		return poolSize;
	}
	public void setPoolSize(int[] poolSize) {
		this.poolSize = poolSize;
	}
}