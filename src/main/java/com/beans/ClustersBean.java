package com.beans;

import com.skplanet.checkmate.querycache.QCClusterManager;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by nazgul33 on 15. 2. 6.
 */
public class ClustersBean implements java.io.Serializable {
    Collection<String> qcClusters = new ArrayList<>();

    public ClustersBean() {
        qcClusters.addAll( QCClusterManager.getInstance().getClusterList() );
    }

    public Collection<String> getQcClusters() {
        return qcClusters;
    }
}
