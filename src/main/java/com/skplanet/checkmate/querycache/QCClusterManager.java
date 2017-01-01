package com.skplanet.checkmate.querycache;

import static com.skplanet.checkmate.ConfigKeys.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCClusterManager {
	
    private static final Logger LOG = LoggerFactory.getLogger(QCClusterManager.class);
    
    private Map<String, QCCluster> clusterMap = new HashMap<>();
    private Map<String, QCClusterOptions> optionMap = new HashMap<>();
    private AtomicLong eventSubscriberIdx = new AtomicLong(0);

    public QCClusterManager(File confDir) throws Exception {
    	
    	File iniFile = new File(confDir, QUERYCACHE_INI_FILE);
        HierarchicalINIConfiguration ini = null;
        
        try {
            LOG.info("reading checkmate::querycache configuration from " + iniFile);
            ini = new HierarchicalINIConfiguration(iniFile);
            
            SubnodeConfiguration global = ini.getSection("global");
            global.setThrowExceptionOnMissing(true);
            
            long partialUpdateInterval = global.getLong(PARTIAL_UPDATE_INTERAVAL, PARTIAL_UPDATE_INTERVAL_DEFAULT);
            long fullUpdateInterval    = global.getLong(FULL_UPDATE_INTERVAL, FULL_UPDATE_INTERVAL_DEFAULT);
            long updateFailSleepTime   = global.getLong(UPDATE_FAIL_SLEEP_TIME, UPDATE_FAIL_SLEEP_TIME_DEFAULT);
            String[] cnames            = global.getStringArray(CLUSTER_NAMES);
            for (String cname: cnames) {
            	
                SubnodeConfiguration sc = ini.getSection(cname);
                QCClusterOptions opts = new QCClusterOptions();
                opts.setWebPort(sc.getInt(CLUSTER_WEB_PORT, CLUSTER_WEB_PORT_DEFFAULT));
                opts.setServers(sc.getStringArray(CLUSTER_SERVERS));
                opts.setMaxCompleteQueries(sc.getInt(CLUSTER_MAX_COMPLETE_QUERIES, CLUSTER_MAX_COMPLETE_QUERIES_DEFAULT));
                opts.setUseWebSocket(sc.getBoolean(CLUSTER_USE_WEBSOCKET, CLUSTER_USER_WEBSOCKE_DEFAULT));
                opts.setFullUpdateInterval(fullUpdateInterval);
                opts.setPartialUpdateInterval(partialUpdateInterval);
                opts.setUpdateFailSleepTime(updateFailSleepTime);
                
                if (ini.containsKey(cname)) {
                    throw new ConfigurationException("duplicate cluster definition");
                }
                optionMap.put(cname, opts);
                
                QCCluster cluster = new QCCluster(cname, optionMap.get(cname));
                clusterMap.put(cname, cluster);
            }
        } catch (Exception e) {
            throw new Exception("error loading configuration. "+iniFile, e);
        }
    }

    public void start() {
    	for (QCCluster cluster:clusterMap.values()) {
    		cluster.start();
    	}
    }

    // stop
    public void stop() {
    	for (QCCluster cluster:clusterMap.values()) {
    		cluster.close();
    	}
    }
    
    public QCCluster getCluster(String name) {
    	return clusterMap.get(name);
    }

    public QCClusterOptions getClusterOptions(String name) {
    	return optionMap.get(name);
    }
    
    public List<String> getClusterNameList() {
    	List<String> list = new ArrayList<>(clusterMap.keySet());
    	Collections.sort(list);
    	return list;
    }
    
    public long getEventSubscriberIdx() {
        return eventSubscriberIdx.incrementAndGet();
    }
}
