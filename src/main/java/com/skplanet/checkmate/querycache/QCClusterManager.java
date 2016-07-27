package com.skplanet.checkmate.querycache;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public QCClusterManager(File confDir) throws Exception {
    	
    	File iniFile = new File(confDir, "querycache.ini");
        HierarchicalINIConfiguration ini = null;

        Map<String, QCClusterOptions> optionMap = new HashMap<>();
        int fullUpdateInterval;
        try {
            LOG.info("reading checkmate::querycache configuration from " + iniFile);
            ini = new HierarchicalINIConfiguration(iniFile);
            
            SubnodeConfiguration globalSection = ini.getSection("global");
            globalSection.setThrowExceptionOnMissing(true);
            
            String[] cnames = globalSection.getStringArray("Clusters");
            for (String cname: cnames) {
            	
                SubnodeConfiguration sc = ini.getSection(cname);
                QCClusterOptions opt = new QCClusterOptions();
                opt.setWebPort(sc.getInt("WebPort"));
                opt.setServers(sc.getStringArray("Servers"));
                opt.setServerMaxCompleteQueries(sc.getInt("MaxCompleteQueries", 100));

                if (ini.containsKey(cname)) {
                    throw new ConfigurationException("duplicate cluster definition");
                }
                optionMap.put(cname, opt);
            }
            fullUpdateInterval = globalSection.getInt("FullUpdateInterval", 10 * 1000);
        }
        catch (Exception e) {
            throw new Exception("error loading configuration. "+iniFile, e);
        }

        // initialize clusters;
        for (String cname:optionMap.keySet()) {
            QCCluster cluster = new QCCluster(cname, optionMap.get(cname), fullUpdateInterval);
            clusterMap.put(cname, cluster);
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

    public List<String> getClusterNameList() {
    	List<String> list = new ArrayList<>(clusterMap.keySet());
    	Collections.sort(list);
    	return list;
    }
}
