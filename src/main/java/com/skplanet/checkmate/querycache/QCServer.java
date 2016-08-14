package com.skplanet.checkmate.querycache;

import static java.lang.String.format;

import java.net.ConnectException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import com.skplanet.querycache.server.cli.ObjectPool;
import com.skplanet.querycache.server.cli.ObjectPool.QCObjectPoolProfile;
import com.skplanet.querycache.server.cli.QueryProfile;
import com.skplanet.querycache.servlet.QCApiConDesc;
import com.skplanet.querycache.servlet.QCApiServerQueries;
import com.skplanet.querycache.servlet.QCEventResponse;
import com.skplanet.querycache.servlet.QCApiServerSystemStats;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCServer {
	
    private static final Logger LOG = LoggerFactory.getLogger(QCServer.class);
    
    private int MAX_COMPLETE_QUERIES = 100;
    
    private Histogram histogram;

    private String name;
    private Map<String, QCQuery> runningMap;
    private Map<String, QCQuery> completeMap;
    private QCCluster cluster;
    private QCClusterOptions clusterOpt;

    private String wsUrl;
    private List<QCApiConDesc> conDescList;
    private QCObjectPoolProfile objectPool;
    private QCApiServerSystemStats systemStats;

    private long lastExceptionTime;

    private QCWebSocketClient wsClient;

    private AtomicInteger queryCount = new AtomicInteger(0);

	private String queriesUrl;
	private String systemUrl;
	private String connectionUrl;
	private String objectpoolUrl;

	private Gson gson = new Gson();

    public QCServer(QCCluster cluster, String name) throws URISyntaxException {
    	
        this.cluster = cluster;
        this.clusterOpt = cluster.getOptions();
        this.name = name;

        this.MAX_COMPLETE_QUERIES = cluster.getOptions().getServerMaxCompleteQueries();
        
        this.runningMap = new HashMap<>();
        this.completeMap = new LimitHashMap<>(MAX_COMPLETE_QUERIES);
       
        this.wsUrl = format("ws://%s:%s%s", name, clusterOpt.getWebPort(), "/api/websocket");
        this.wsClient = new QCWebSocketClient(this, wsUrl);
    
        int webPort = cluster.getOptions().getWebPort();
        this.queriesUrl =    format("http://%s:%s/api/queries",     name, webPort);
        this.systemUrl =     format("http://%s:%s/api/system",      name, webPort);
        this.connectionUrl = format("http://%s:%s/api/connections", name, webPort);
        this.objectpoolUrl = format("http://%s:%s/api/objectpool",  name, webPort);
        
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
    	wsClient.start();
    }
    
    public void stop() {
    	wsClient.stop();
    }
    
    public int getAndResetQueryCount() {
        int count = queryCount.getAndSet(0);
        histogram.update(count);
        return count;
    }

    private void setLastException() {
        this.lastExceptionTime = System.currentTimeMillis();
    }

    public void updateApi() {
    	
    	if (lastExceptionTime > 0 && lastExceptionTime + 60*1000 > System.currentTimeMillis()) {
    		return;
    	}
    	LOG.debug("updateApi {}.{}", cluster.getName(), name);
        updateApiQueries();
        updateApiConnections();
        updateApiObjectPools();
        updateApiSystem();
    }

    private void updateApiQueries() {
    	
    	long t0 = System.currentTimeMillis();
        try {
            String content = HttpUtil.get(queriesUrl);
            QCApiServerQueries queries = gson.fromJson(content, QCApiServerQueries.class);

            List<QCQuery> runningList = new ArrayList<>(queries.getRunningQueries().size());
            for (QueryProfile qi:queries.getRunningQueries()) {
            	QCQuery query = new QCQuery(cluster.getName(), name, qi);
            	runningList.add(query);
            }
        	List<QCQuery> completeList = new ArrayList<>(queries.getCompleteQueries().size());
        	for (QueryProfile qi:queries.getCompleteQueries()) {
        		QCQuery query = new QCQuery(cluster.getName(), name, qi);
        		completeList.add((query));
        	}

        	synchronized(runningMap) {

        		Iterator<Entry<String, QCQuery>> iter = runningMap.entrySet().iterator();
        		while (iter.hasNext()) {
        			Entry<String, QCQuery> entry = iter.next();
        			QCQuery value = entry.getValue();
        			if (!runningList.contains(value)) {
        				int compIdx = completeList.indexOf(value);
        				if (compIdx > -1) {
            				cluster.addQueryEvent(QCEventResponse.REMOVE, completeList.get(compIdx));
        				} else {
            				cluster.addQueryEvent(QCEventResponse.REMOVE, value);
        				}
        				iter.remove();
        			}
        		}

        		for (QCQuery query:runningList) {
        			QCQuery rquery = runningMap.get(query.getCuqId());
        			if (rquery != null) {
        				if (QCQuery.update(rquery, query)) {
        					cluster.addQueryEvent(QCEventResponse.UPDATE, rquery);
        				}
        			} else {
        				runningMap.put(query.getCuqId(), query);
        				cluster.addQueryEvent(QCEventResponse.ADD, query);
        				queryCount.incrementAndGet();
        			}
        		}
        	}
            
        	synchronized (completeMap) {
                int added = 0;
                for (QCQuery query : completeList) {
                    // complete query has no changes. process new complete queries only.
                    if (!completeMap.containsKey(query.getCuqId())) {
                        completeMap.put(query.getCuqId(), query);
                        cluster.addQueryEvent(QCEventResponse.REMOVE, query);
                        added++;
                    }
                }
                LOG.debug("{}.{} : complete query. add={}, total={}", 
                		cluster.getName(), name, added, completeMap.size());
            }
        	
        	long t1 = System.currentTimeMillis();
        	LOG.debug("update queries {} {} : took={}, run={}, complete={}", 
        			cluster.getName(), name, (t1-t0), runningList.size(), completeList.size());
        } catch (ConnectException e) {
        	LOG.error("{}.{}: updating queries : {} {}",
            		cluster.getName(), name, queriesUrl, e.getMessage());
        } catch (Exception e) {
            LOG.error("{}.{}: updating queries : {} {}",
            		cluster.getName(), name, queriesUrl, e.getMessage(), e);
            setLastException();
        }
    }
    
    private void updateApiConnections() {
    	conDescList = null;
        try {
        	String content = HttpUtil.get(connectionUrl);
            Gson gSon = new Gson();
            conDescList = gSon.fromJson(content, new TypeToken<ArrayList<QCApiConDesc>>(){}.getType());
            if (LOG.isDebugEnabled()) {
                for (QCApiConDesc con: conDescList) {
                    LOG.debug("{}.{} con={} free={} using={}", 
                    		cluster.getName(), name, 
                    		con.getDriver(), con.getFree(), con.getUsing());
                }
            }
        } catch (ConnectException e) {
        	LOG.error("{}.{}: updating connections : {} {}", 
            		cluster.getName(), name, connectionUrl, e.getMessage());
        } catch (Exception e) {
            LOG.error("{}.{}: updating connections : {} {}", 
            		cluster.getName(), name, connectionUrl, e.getMessage(), e);
            setLastException();
        }
    }

    private void updateApiObjectPools() {
    	objectPool = null;
        try {
            String content = HttpUtil.get(objectpoolUrl);
            Gson gSon = new Gson();
            objectPool = gSon.fromJson(content, ObjectPool.QCObjectPoolProfile.class);
            LOG.debug("{}.{} pool {} {} {} {} {}",
            		cluster.getName(), 
            		name,
            		objectPool.getPoolSize()[0],
                    objectPool.getPoolSize()[1],
                    objectPool.getPoolSize()[2],
                    objectPool.getPoolSize()[3]);
        } catch (ConnectException e) {
        	LOG.error("{}.{}: updating object pools : {} {}", 
            		cluster.getName(), name, objectpoolUrl, e.getMessage());
            setLastException();
        } catch (Exception e) {
        	LOG.error("{}.{}: updating object pools : {} {}", 
            		cluster.getName(), name, objectpoolUrl, e.getMessage(), e);
            setLastException();
        }
    }

    private void updateApiSystem() {
    	systemStats = null;
        try {
        	String content = HttpUtil.get(systemUrl);
            Gson gSon = new Gson();
            systemStats = gSon.fromJson(content, QCApiServerSystemStats.class);
            
            LOG.debug("{}.{} total threads={}, runtime memused={}", 
            		cluster.getName(), name, 
            		systemStats.getThreads().getTotalThreads(),
                    (systemStats.getJvm().getMemTotal() - systemStats.getJvm().getMemFree())
                    );
        } catch (ConnectException e) {
        	LOG.error("{}.{}: updating system stats : {} {}", 
            		cluster.getName(), name, systemUrl, e.getMessage());
            setLastException();
        } catch (Exception e) {
        	LOG.error("{}.{}: updating system stats : {} {}", 
            		cluster.getName(), name, systemUrl, e.getMessage(), e);
            setLastException();
        }
    }

    public QCApiServerSystemStats getSystemStats() {
        return systemStats;
    }

    public QCObjectPoolProfile getObjectPool() {
        return objectPool;
    }

    public List<QCApiConDesc> getConnDescList() {
        return conDescList;
    }
    
    public List<QCQuery> getRunningQueryList() {
    	synchronized(runningMap) {
        	return new ArrayList<>(runningMap.values());
    	}
    }
    
    public QCQuery getRunningQueryByCuqId(String cuqId) {
    	synchronized(runningMap) {
    		return runningMap.get(cuqId);
    	}
    }
    
    public List<QCQuery> getCompleteQueryList() {
    	synchronized(completeMap) {
        	return new ArrayList<>(completeMap.values());
    	}
    }
    
    public void processWebSocketAddEvent(QueryProfile queryImport) {
    	
    	QCQuery query = new QCQuery(cluster.getName(), name, queryImport);
    	synchronized(runningMap) {
    		QCQuery rquery = runningMap.get(query.getCuqId());
    		if (rquery != null) {
    			boolean updated = QCQuery.update(rquery, query);
    			if (updated) {
    				cluster.addQueryEvent(QCEventResponse.UPDATE, rquery);
    			}
    		} else {
    			runningMap.put(query.getCuqId(), query);
    			queryCount.incrementAndGet();
    			cluster.addQueryEvent(QCEventResponse.ADD, query);
    		}
    	}
    }

    public void processWebSocketRemoveEvent(QueryProfile queryImport) {
    	
    	QCQuery query = new QCQuery(cluster.getName(), name, queryImport);
    	synchronized(runningMap) {
        	if (runningMap.containsKey(query.getCuqId())) {
                runningMap.remove(query.getCuqId());
        	}
    	}
    	synchronized(completeMap) {
    		if (!completeMap.containsKey(query.getCuqId())) {
    			completeMap.put(query.getCuqId(), query);
                cluster.addQueryEvent(QCEventResponse.REMOVE, query);
    		}
    	}
    }

    private class LimitHashMap<K,V> extends LinkedHashMap<K,V> {
    	
		private static final long serialVersionUID = -3768283519827515112L;

		private int MAX_SIZE = 0;
		
		public LimitHashMap(int maxSize) {
			super();
			this.MAX_SIZE = maxSize;
		}
		
		@Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > MAX_SIZE;
        }
    };
}
