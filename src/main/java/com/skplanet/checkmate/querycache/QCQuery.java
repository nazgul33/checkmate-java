package com.skplanet.checkmate.querycache;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skplanet.querycache.server.cli.QueryProfile;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCQuery {

	private static final Logger LOG = LoggerFactory.getLogger(QCQuery.class);
	
    private String cluster;
    private String server;
    private String id;
    private String backend;
    private String user;
    private String type;
    private String statement;
    private String client;
    private String state;
    private long rowCnt = -1;
    private long startTime;
    private long endTime = 0;
    // 0:exec/1:getmeta/2:fetch/3:stmtclose or
    // 1:getschemas/2:fetch
    private Long[] timeHistogram;
    private long[] execProfile;
    private long[] fetchProfile;

    private String cuqId;
    private String cancelUrl;
    
    private long updateTime;
    private long updateCount;
    private boolean anormal = false;

    public QCQuery(String cluster, String server, QueryProfile qObj) {
    	
    	this.cluster       = cluster;
        this.server        = server;
        this.id            = qObj.getQueryId();
        this.backend       = qObj.getConnType();
        this.user          = qObj.getUser();
        this.type          = (qObj.getQueryType()!=null?qObj.getQueryType().name():null);
        this.statement     = qObj.getQueryStr();
        this.client        = qObj.getClientIp();
        this.state         = (qObj.getStmtState()!=null?qObj.getStmtState().name():null);
        this.rowCnt        = qObj.getRowCnt();
        this.startTime     = qObj.getStartTime();
        this.endTime       = qObj.getEndTime();
        this.timeHistogram = qObj.getTimeHistogram();
        this.execProfile   = qObj.getExecProfile();
        this.fetchProfile  = qObj.getFetchProfile();

        this.cuqId = getCuqId(cluster, server, qObj);
        this.cancelUrl = "/api/qc/cancelQuery?cluster="+cluster+"&cuqId="+cuqId;

        this.updateCount = 0;
        this.updateTime = System.currentTimeMillis();
}

	public String getCluster() {
		return cluster;
	}

	public void setCluster(String cluster) {
		this.cluster = cluster;
	}

	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getBackend() {
		return backend;
	}

	public void setBackend(String backend) {
		this.backend = backend;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getStatement() {
		return statement;
	}

	public void setStatement(String statement) {
		this.statement = statement;
	}

	public String getClient() {
		return client;
	}

	public void setClient(String client) {
		this.client = client;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
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

	public Long[] getTimeHistogram() {
		return timeHistogram;
	}

	public void setTimeHistogram(Long[] timeHistogram) {
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

	public long getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(long updateTime) {
		this.updateTime = updateTime;
	}

	public long getUpdateCount() {
		return updateCount;
	}

	public void setUpdateCount(long updateCount) {
		this.updateCount = updateCount;
	}

	public String getCuqId() {
		return cuqId;
	}

	public void setCuqId(String cuqId) {
		this.cuqId = cuqId;
	}

	public String getCancelUrl() {
		return cancelUrl;
	}

	public void setCancelUrl(String cancelUrl) {
		this.cancelUrl = cancelUrl;
	}

	public boolean isAnormal() {
		return anormal;
	}

	public void setAnormal(boolean anormal) {
		this.anormal = anormal;
	}

	public static boolean update(QCQuery oq, QCQuery nq) {
		
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        boolean updated = false;
        if (!oq.getType().equals(nq.getType())) {
        	LOG.debug("query {} update type {} -> {}", oq.getId(), oq.getType(), nq.getType());
            oq.setType(nq.getType());
            updated = true;
        }
        if (!oq.getState().equals(nq.getState())) {
            LOG.debug("query {} update state {} -> {}", oq.getId(), oq.getState(), nq.getState());
            oq.setState(nq.getState());
            updated = true;
        }
        if (oq.getRowCnt() != nq.getRowCnt() ) {
            LOG.debug("query {} update rowCnt {} -> {}", oq.getId(), oq.getRowCnt(), nq.getRowCnt());
            oq.setRowCnt(nq.getRowCnt());
            updated = true;
        }
        if (oq.getEndTime() != nq.getEndTime()) {
            LOG.debug("query {} update endTime {} -> %s", oq.getId(), 
            		sdf.format(new Date(oq.getEndTime())), 
            		sdf.format(new Date(nq.getEndTime())));
            oq.setEndTime(nq.endTime);
            updated = true;
        }

        if (updated) {
            oq.setUpdateTime(System.currentTimeMillis());
            oq.setUpdateCount(oq.getUpdateCount()+1);
        }
        return updated;
    }

    public static String getCuqId(String cluster, String server, QueryProfile qi) {
    	String key = server+qi.getConnType()+qi.getQueryId();
        String cuqid = Base64.encodeBase64String(key.trim().getBytes());
        return cuqid.replace('+',':').replace('/','.').replace('=','-');
    }

	public boolean equals(Object o) {
		return o instanceof QCQuery && cuqId.equals(((QCQuery)o).getCuqId());
	}
}
