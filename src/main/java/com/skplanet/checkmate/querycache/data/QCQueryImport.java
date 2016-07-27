package com.skplanet.checkmate.querycache.data;

public class QCQueryImport {
	
	private String queryId;
	private String connType;
	private String user;
	private String queryType;
	private String queryStr;
	private String clientIp;
	private String stmtState;
	private long rowCnt;
	private long startTime;
	private long endTime;
	// 0:exec/1:getmeta/2:fetch/3:stmtclose or
	// 1:getschemas/2:fetch
	private long[] timeHistogram = {0,0,0,0};
	private long[] execProfile   = null;
	private long[] fetchProfile  = {0,0,0,-1,-1,-1,-1,0,0,-1,-1};
	public String getQueryId() {
		return queryId;
	}
	public void setQueryId(String queryId) {
		this.queryId = queryId;
	}
	public String getConnType() {
		return connType;
	}
	public void setConnType(String connType) {
		this.connType = connType;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public String getQueryType() {
		return queryType;
	}
	public void setQueryType(String queryType) {
		this.queryType = queryType;
	}
	public String getQueryStr() {
		return queryStr;
	}
	public void setQueryStr(String queryStr) {
		this.queryStr = queryStr;
	}
	public String getClientIp() {
		return clientIp;
	}
	public void setClientIp(String clientIp) {
		this.clientIp = clientIp;
	}
	public String getStmtState() {
		return stmtState;
	}
	public void setStmtState(String stmtState) {
		this.stmtState = stmtState;
	}
	public long getRowCnt() {
		return rowCnt;
	}
	public void setRowCnt(long rowCnt) {
		this.rowCnt = rowCnt;
	}
	public long getStartTime() {
		return startTime;
	}
	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	public long getEndTime() {
		return endTime;
	}
	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}
	public long[] getTimeHistogram() {
		return timeHistogram;
	}
	public void setTimeHistogram(long[] timeHistogram) {
		this.timeHistogram = timeHistogram;
	}
	public long[] getExecProfile() {
		return execProfile;
	}
	public void setExecProfile(long[] execProfile) {
		this.execProfile = execProfile;
	}
	public long[] getFetchProfile() {
		return fetchProfile;
	}
	public void setFetchProfile(long[] fetchProfile) {
		this.fetchProfile = fetchProfile;
	}
}