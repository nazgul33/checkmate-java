package com.skplanet.checkmate.querycache.data;

import java.util.Collections;
import java.util.List;

import com.skplanet.querycache.server.cli.ObjectPool.QCObjectPoolProfile;
import com.skplanet.querycache.servlet.QCApiConDesc;

public class ClusterServerPoolInfo {
	private String server;
	private QCObjectPoolProfile objPool;
	private List<QCApiConDesc> connPoolList;
	
	public ClusterServerPoolInfo(){}
	
	@SuppressWarnings("unchecked")
	public ClusterServerPoolInfo(String server, QCObjectPoolProfile objPool, List<QCApiConDesc> connPoolList) {
		this.server = server;
		this.objPool = (objPool!=null?objPool:new QCObjectPoolProfile());
		this.connPoolList = (connPoolList!=null?connPoolList:Collections.EMPTY_LIST);
	}
	
	public String getServer() {
		return server;
	}
	public void setServer(String server) {
		this.server = server;
	}
	public QCObjectPoolProfile getObjPool() {
		return objPool;
	}
	public void setObjPool(QCObjectPoolProfile objPool) {
		this.objPool = objPool;
	}
	public List<QCApiConDesc> getConnPoolList() {
		return connPoolList;
	}
	public void setConnPoolList(List<QCApiConDesc> connPoolList) {
		this.connPoolList = connPoolList;
	}
}