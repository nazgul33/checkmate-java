package com.skplanet.checkmate.querycache;

import static java.lang.String.format;

import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.utils.HttpUtil;
import com.skplanet.querycache.server.cli.ObjectPool.QCObjectPoolProfile;
import com.skplanet.querycache.servlet.QCApiConDesc;
import com.skplanet.querycache.servlet.QCApiServerQueries;
import com.skplanet.querycache.servlet.QCApiServerSystemStats;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCServer {
	
    private static final Logger LOG = LoggerFactory.getLogger(QCServer.class);
    
    private Histogram histogram;

    private String name;
    private QCCluster cluster;
    private QCClusterOptions opts;
    private String wsUrl;

    private long lastExceptionTime;

    private QCWebSocketClient wsClient;

    private AtomicInteger queryCount = new AtomicInteger(0);

	private String queriesUrl;
	private String systemUrl;
	private String connectionUrl;
	private String objectpoolUrl;

	private Gson gson = new Gson();
	private Type connectionType = new TypeToken<ArrayList<QCApiConDesc>>(){}.getType();

    public QCServer(QCCluster cluster, String name) throws URISyntaxException {
    	
        this.cluster = cluster;
        this.opts = cluster.getOptions();
        this.name = name;

        int webPort = cluster.getOptions().getWebPort();
        
        this.wsUrl         = format("ws://%s:%s/api/websocket",     name, webPort);
        this.queriesUrl    = format("http://%s:%s/api/queries",     name, webPort);
        this.systemUrl     = format("http://%s:%s/api/system",      name, webPort);
        this.connectionUrl = format("http://%s:%s/api/connections", name, webPort);
        this.objectpoolUrl = format("http://%s:%s/api/objectpool",  name, webPort);

        if (cluster.getOptions().isUseWebSocket()) {
            this.wsClient = new QCWebSocketClient(this, wsUrl);
        }

        String metricName = format("queries.%s.%s",cluster.getName(),name);
        histogram = new Histogram( new SlidingTimeWindowReservoir(5L, TimeUnit.MINUTES) );
        CheckMateServer.getMetrics().register(metricName, histogram);
    }
    
    public String getName() {
    	return name;
    }
    
    public QCCluster getCluster() {
    	return cluster;
    }

    public void start() {
    	if (wsClient != null) {
    		wsClient.start();
    	}
    }
    
    public void stop() {
    	if (wsClient != null) {
    		wsClient.stop();
    	}
    }
    
    public int getAndResetQueryCount() {
        int count = queryCount.getAndSet(0);
        histogram.update(count);
        return count;
    }

    private void setLastException() {
        this.lastExceptionTime = System.currentTimeMillis();
    }

    private boolean isCheckable() {
    	return lastExceptionTime == 0 || 
    			lastExceptionTime + opts.getUpdateFailSleepTime() < System.currentTimeMillis()   ; 	
    }
    
    public void updatePartial() {
    	updateQueries();
    }
    
    public void updateFull() {
        updateConnections();
        updateObjectPools();
        updateSystem();
    }

    private void updateQueries() {
    	if (!isCheckable()) {
    		return;
    	}
        try {
            String content = HttpUtil.get(queriesUrl);
            QCApiServerQueries queries = gson.fromJson(content, QCApiServerQueries.class);
            cluster.addRunningQueries(queries.getRunningQueries());
            cluster.addCompleteQueries(queries.getCompleteQueries());
        } catch (ConnectException e) {
        	LOG.error("{}.{}: updating queries : {} {}",
            		cluster.getName(), name, queriesUrl, e.getMessage());
        	setLastException();
        } catch (Exception e) {
            LOG.error("{}.{}: updating queries : {} {}",
            		cluster.getName(), name, queriesUrl, e.getMessage(), e);
            setLastException();
        }
    }
    
    private void updateConnections() {
    	if (!isCheckable()) {
    		return;
    	}
        try {
        	String content = HttpUtil.get(connectionUrl);
        	cluster.putConnections(name, gson.fromJson(content, connectionType));
        } catch (ConnectException e) {
        	cluster.putConnections(name, null);
        	LOG.error("{}.{}: updating connections : {} {}", 
            		cluster.getName(), name, connectionUrl, e.getMessage());
        	setLastException();
        } catch (Exception e) {
        	cluster.putConnections(name, null);
            LOG.error("{}.{}: updating connections : {} {}", 
            		cluster.getName(), name, connectionUrl, e.getMessage(), e);
            setLastException();
        }
    }

    private void updateObjectPools() {
    	if (!isCheckable()) {
    		return;
    	}
        try {
            String content = HttpUtil.get(objectpoolUrl);
            cluster.putObjectPools(name, gson.fromJson(content, QCObjectPoolProfile.class));
        } catch (ConnectException e) {
            cluster.putObjectPools(name, null);
        	LOG.error("{}.{}: updating object pools : {} {}", 
            		cluster.getName(), name, objectpoolUrl, e.getMessage());
            setLastException();
        } catch (Exception e) {
        	cluster.putObjectPools(name, null);
        	LOG.error("{}.{}: updating object pools : {} {}", 
            		cluster.getName(), name, objectpoolUrl, e.getMessage(), e);
            setLastException();
        }
    }

    private void updateSystem() {
    	if (!isCheckable()) {
    		return;
    	}
        try {
        	String content = HttpUtil.get(systemUrl);
            cluster.putSystemStats(name, gson.fromJson(content, QCApiServerSystemStats.class));
        } catch (ConnectException e) {
        	cluster.putSystemStats(name, null);
        	LOG.error("{}.{}: updating system stats : {} {}", 
            		cluster.getName(), name, systemUrl, e.getMessage());
            setLastException();
        } catch (Exception e) {
        	cluster.putSystemStats(name, null);
        	LOG.error("{}.{}: updating system stats : {} {}", 
            		cluster.getName(), name, systemUrl, e.getMessage(), e);
            setLastException();
        }
    }
}
