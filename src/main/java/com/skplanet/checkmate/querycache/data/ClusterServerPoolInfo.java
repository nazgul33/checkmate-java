package com.skplanet.checkmate.querycache.data;

import java.util.Collections;
import java.util.List;

public class ClusterServerPoolInfo {
	private String server;
	private ServerObjectPool objPool;
	private List<ServerConDesc> connPoolList;
	
	public ClusterServerPoolInfo(){}
	
	public ClusterServerPoolInfo(String server, ServerObjectPool objPool, List<ServerConDesc> connPoolList) {
		this.server = server;
		this.objPool = (objPool!=null?objPool:new ServerObjectPool());
		this.connPoolList = (connPoolList!=null?connPoolList:Collections.EMPTY_LIST);
	}
	
	public String getServer() {
		return server;
	}
	public void setServer(String server) {
		this.server = server;
	}
	public ServerObjectPool getObjPool() {
		return objPool;
	}
	public void setObjPool(ServerObjectPool objPool) {
		this.objPool = objPool;
	}
	public List<ServerConDesc> getConnPoolList() {
		return connPoolList;
	}
	public void setConnPoolList(List<ServerConDesc> connPoolList) {
		this.connPoolList = connPoolList;
	}
}