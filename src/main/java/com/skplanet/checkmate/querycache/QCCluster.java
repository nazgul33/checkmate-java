package com.skplanet.checkmate.querycache;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.google.gson.Gson;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.querycache.data.ClusterServerPoolInfo;
import com.skplanet.checkmate.querycache.data.ClusterServerSysStats;
import com.skplanet.checkmate.querycache.data.QCQueryEvent;
import com.skplanet.checkmate.querycache.data.ServerObjectPool;
import com.skplanet.checkmate.servlet.CMQCServerWebSocket;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCCluster {
	
    private static final Logger LOG = LoggerFactory.getLogger(QCCluster.class);
    
    private String name;
    private QCClusterOptions opt;

    private Map<String, QCServer> serverMap = new HashMap<>();

    private Timer notifyTimer = new Timer(true);
    private Histogram histogram;
    
    private long fullUpdateInterval;
    private Timer updateTimer = new Timer(true);
    
//    private static final long longRunningLimit = 900L * 1000L; // 900 sec

    private final static long retentionTime = 200;
    private final LinkedList<QCQueryEvent> evtQueueList = new LinkedList<>();
    
    private Map<Integer, CMQCServerWebSocket> realtimeMessageReceiverMap = new HashMap<>();
    private AtomicInteger realtimeMessageReceiversId = new AtomicInteger(0);

    public QCCluster(String name, QCClusterOptions opt, long fullUpdateInterval) throws URISyntaxException {
    	
        this.name = name;
        this.opt = opt;
        this.fullUpdateInterval = fullUpdateInterval;
        
        for (String serverName : opt.getServers()) {
            if (serverMap.containsKey(serverName)) {
            	LOG.warn("duplicate server name {}", serverName);
            } else {
                serverMap.put(serverName, new QCServer(this, serverName));
            }
        }
        histogram = new Histogram( new SlidingTimeWindowReservoir(5L, TimeUnit.MINUTES) );
        
        CheckMateServer.getMetrics().register("queries."+name, histogram);
    }

    public String getName() {
		return name;
	}

    public QCServer getServer(String name) {
        return serverMap.get(name);
    }
    
    public QCClusterOptions getOptions() {
        return opt;
    }
    
    public void start() {
    	
    	for (QCServer server:serverMap.values()) {
    		server.start();
    	}
    	
    	notifyTimer.schedule(new TimerTask() {
    		// CheckMate 의 websocket 에 연결된 client 에게 실행중인 쿼리를 발송하는 타이머
    		@Override
    		public void run() {
    			notifySubscribers();
    		}
    	}, 0, 50);

//       notifyTimer.schedule(new TimerTask() {
//       @Override
//       public void run() {
//           List<QCQuery> list = new ArrayList<>();
//           synchronized (runningQueriesMap) {
//           	for (QCQuery query:runningQueriesMap.values()) {
//           		if (isAnomalQuery(query)) {
//           			list.add(query);
//           		}
//           	}
//           }
//           processAnomalQuery(list);
//       }
//      }, 5000L, 5000L);

    	 updateTimer.schedule(new TimerTask() {
    		 @Override
    		 public void run() {
    			 updateApi();
    		 }
    	 }, 0, fullUpdateInterval);

    	 updateTimer.scheduleAtFixedRate(new TimerTask() {
    		 @Override
    		 public void run() {
    			 updateCounters();
    		 }
    	 }, 60*1000, 60*1000);
    }
    
    public void close() {
    	updateTimer.cancel();
    	
    	for (QCServer server:serverMap.values()) {
    		server.stop();
    	}
    }
    
	private void updateCounters() {
        int count = 0;
        for (QCServer server:serverMap.values()) {
            count += server.getAndResetQueryCount();
        }
        histogram.update(count);
    }
	
	private void updateApi() {
        // update all servers
        for (QCServer server:serverMap.values()) {
        	server.updateApi();
        }
    }
    
    public List<ClusterServerSysStats> getServerInfo() {
        List<ClusterServerSysStats> list = new ArrayList<>(serverMap.size());
    	for (QCServer server:serverMap.values()) {
    		list.add(new ClusterServerSysStats(server.getName(), server.getSystemStats()));
    	}
        Collections.sort(list, new Comparator<ClusterServerSysStats>() {
            @Override
            public int compare(ClusterServerSysStats o1, ClusterServerSysStats o2) {
                return o1.getServer().compareTo(o2.getServer());
            }
        });
        return list;
    }

    @SuppressWarnings("unchecked")
	public List<ClusterServerPoolInfo> getPoolInfo() {
        List<ClusterServerPoolInfo> list = new ArrayList<>(serverMap.size());
        for (QCServer server:serverMap.values()) {
    		list.add(new ClusterServerPoolInfo(server.getName(), 
    				server.getObjectPool(), server.getConnDescList()));
    	}
        Collections.sort(list, new Comparator<ClusterServerPoolInfo>() {
            @Override
            public int compare(ClusterServerPoolInfo o1, ClusterServerPoolInfo o2) {
                return o1.getServer().compareTo(o2.getServer());
            }
        });
        return list;
    }

    // Web UI
    public QCQuery getRunningQueryByCuqId(String cuqId) {
    	for (QCServer server:serverMap.values()) {
            return server.getRunningQueryByCuqId(cuqId);
        }
    	return null;
    }
    
    // Web UI
    public List<QCQuery> getRunningQueries() {
        List<QCQuery> list = new ArrayList<>();
        for (QCServer server:serverMap.values()) {
        	list.addAll(server.getRunningQueryList());
        }
        Collections.sort(list, new Comparator<QCQuery>() {
            @Override
            public int compare(QCQuery o1, QCQuery o2) {
            	return Long.compare(o2.getStartTime(), o1.getStartTime());
            }
        });
        return list;
    }
    
    public List<QCQuery> getCompleteQueries() {
    	List<QCQuery> list = new ArrayList<>();
        for (QCServer server:serverMap.values()) {
        	list.addAll(server.getCompleteQueryList());
        }
        Collections.sort(list, new Comparator<QCQuery>() {
            @Override
            public int compare(QCQuery o1, QCQuery o2) {
            	return Long.compare(o2.getStartTime(), o1.getStartTime());
            }
        });
        return list;
    }
    
    public void addQueryEvent(String type, QCQuery query) {
    	
    	QCQueryEvent evt = new QCQueryEvent(type, query);
    	
        synchronized (evtQueueList) {
            if (QCQueryEvent.RUN_QUERY_REMOVED.equals(evt.getMsgType())) {
                Iterator<QCQueryEvent> it = evtQueueList.iterator();
                while (it.hasNext()) {
                	QCQueryEvent rqe = it.next();
                    if (evt.getCuqId().equals(rqe.getCuqId())) {
                        it.remove();
                    }
                }
            }
            evtQueueList.addLast(evt);
        }
    }
    
    /**
     * CheckMate 의 websocket 에 연결된 client 등록
     * @param ws
     * @return
     */
    public int subscribe(CMQCServerWebSocket ws) {
        int id = realtimeMessageReceiversId.getAndAdd(1);
        realtimeMessageReceiverMap.put(id, ws);
        return id;
    }

    /**
     * CheckMate 의 websocket 연결이 끊기면 client 삭제
     * @param id
     */
    public void unsubscribe(int id) {
    	realtimeMessageReceiverMap.remove(id);
    }
    
    /**
     * CheckMate 의 websocket 에 연결한 client running query event 전송
     */
    private void notifySubscribers() {
    	
        long retainAfter = System.currentTimeMillis() - retentionTime; // send events older than 200ms

        List<QCQueryEvent> el = new ArrayList<>();
        synchronized (evtQueueList) {
            Iterator<QCQueryEvent> it = evtQueueList.iterator();
            while (it.hasNext()) {
                QCQueryEvent evt = it.next();
                if (evt.getTimestamp() < retainAfter) {
                    el.add(evt);
                    it.remove();
                }
            }
        }

        if (el.size()>0) {
            Gson gson = new Gson();
            for (QCQueryEvent evt: el) {
                String msg = gson.toJson(evt);
                for (CMQCServerWebSocket ws : realtimeMessageReceiverMap.values()) {
                    ws.sendMessage(msg);
                }
            }
        }
    }

//    private boolean isAnomalQuery(QCQuery query) {
//    	return query.isAnormal() == false && 
//    			(query.getStartTime() + longRunningLimit) < System.currentTimeMillis() &&
//    			("EXEC".equals(query.getState()) || "INIT".equals(query.getState()) );
//    }
//    
//    private void processAnomalQuery(List<QCQuery> list) {
//    	for (QCQuery query:list) {
//    		query.setAnormal(true);
//        	LOG.info("Slow query {}:{}", query.getBackend(), query.getId());
//        	MailSender.send(
//            new QCQueryMail(query, "[QC] Slow Query by "+ query.getUser(), "Slow query detected by CheckMate."));
//    	}
//    }
}
