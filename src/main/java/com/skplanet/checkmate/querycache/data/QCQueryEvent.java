package com.skplanet.checkmate.querycache.data;

import com.skplanet.checkmate.querycache.QCQuery;

public class QCQueryEvent {
	
    public static final String RUN_QUERY_ADDED = "runningQueryAdded";
    public static final String RUN_QUERY_UPDATED = "runningQueryUpdated";
    public static final String RUN_QUERY_REMOVED = "runningQueryRemoved";
    
    private long timestamp;
    private String msgType;
    private QCQuery query;
    private String cuqId;
    
    public QCQueryEvent(String msgType, QCQuery query) {
    	 this.timestamp = System.currentTimeMillis();
    	 this.msgType = msgType;
    	 this.query = query;
    	 this.cuqId = query.getCuqId();
    }

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getMsgType() {
		return msgType;
	}

	public void setMsgType(String msgType) {
		this.msgType = msgType;
	}

	public QCQuery getQuery() {
		return query;
	}

	public void setQuery(QCQuery query) {
		this.query = query;
	}

	public String getCuqId() {
		return cuqId;
	}

	public void setCuqId(String cuqId) {
		this.cuqId = cuqId;
	}
}