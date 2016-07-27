package com.beans;

import java.util.ArrayList;
import java.util.Collection;

import com.skplanet.checkmate.CheckMateServer;

/**
 * Created by nazgul33 on 15. 2. 6.
 */
public class ClustersBean implements java.io.Serializable {
	
	
	private static final long serialVersionUID = 2387849279567737354L;
	
	private Collection<String> qcClusters = new ArrayList<>();

    public ClustersBean() {
        qcClusters.addAll( CheckMateServer.getClusterManager().getClusterNameList() );
    }

    public Collection<String> getQcClusters() {
        return qcClusters;
    }
}
