package com.skplanet.checkmate.querycache;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.google.gson.Gson;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.ConfigKeys;
import com.skplanet.checkmate.querycache.data.ClusterServerPoolInfo;
import com.skplanet.checkmate.querycache.data.ClusterServerSysStats;
import com.skplanet.checkmate.servlet.CMQCWebSocket;
import com.skplanet.querycache.server.cli.ObjectPool.QCObjectPoolProfile;
import com.skplanet.querycache.server.cli.QueryProfile;
import com.skplanet.querycache.server.common.SQLUtil;
import com.skplanet.querycache.servlet.QCApiConDesc;
import com.skplanet.querycache.servlet.QCApiServerSystemStats;
import com.skplanet.querycache.servlet.QCWebSocket.ChangedProfile;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCCluster {

	private static final Logger LOG = LoggerFactory.getLogger(QCCluster.class);

	private int MAX_COMPLETE_QUERIES = 100;

	private String name;
	private QCClusterOptions opts;

	private Map<String, QCServer> serverMap = new HashMap<>();

	private Map<String, QueryProfile> runningMap = new HashMap<>();
	private ArrayDeque<QueryProfile> completeList = new ArrayDeque<>();
	
	private Map<String, QCObjectPoolProfile> objectPoolMap = new HashMap<>();
	private Map<String, QCApiServerSystemStats> systemStatMap = new HashMap<>();
	private Map<String, List<QCApiConDesc>> connectionMap = new HashMap<>();

	private EventManager eventManager;

	private Histogram histogram;

	private long partialUpdateInterval;
	private long fullUpdateInterval;

	private Thread eventThread;
	private Thread fullUpdateThread;
	private Thread partialUpdateThread;
	private Timer histogramTimer = new Timer(true);
	
	private boolean running = true;

	private Gson gson = new Gson();

	public QCCluster(String name, QCClusterOptions opts) throws URISyntaxException, IOException {

		this.name = name;
		this.opts = opts;
		this.fullUpdateInterval    = opts.getFullUpdateInterval();
		this.partialUpdateInterval = opts.getPartialUpdateInterval();

		this.MAX_COMPLETE_QUERIES = opts.getMaxCompleteQueries();

		for (String serverName : opts.getServers()) {
			if (serverMap.containsKey(serverName)) {
				throw new IOException("duplicate server name=" + serverName);
			} else {
				serverMap.put(serverName, new QCServer(this, serverName));
			}
		}
		
		histogram = new Histogram( new SlidingTimeWindowReservoir(5L, TimeUnit.MINUTES) );

		CheckMateServer.getMetrics().register("queries."+name, histogram);

		if (opts.isUseWebSocket()) {
			eventManager = new EventManager();
			eventThread = new Thread() {
				@Override
				public void run() {
					LOG.info("Cluster {} WebSocket Start", name);

					while (running) {
						eventManager.send();
						try {
							TimeUnit.MILLISECONDS.sleep(1000);
						} catch (InterruptedException e) {
							LOG.error("Cluster {} WebSocket interrupted", name);
							break;
						}
					}
					running = false;
					LOG.info("Cluster {} WebSocket Stop", name);
				}
			};
			eventThread.start();
		} else {
			this.partialUpdateInterval = Math.min(this.partialUpdateInterval, 3000);
			this.fullUpdateInterval    = Math.min(this.fullUpdateInterval, 5000);
		}
	}

	public String getName() {
		return name;
	}

	public QCServer getServer(String name) {
		return serverMap.get(name);
	}

	public QCClusterOptions getOptions() {
		return opts;
	}

	public void start() {

		for (QCServer server:serverMap.values()) {
			server.start();
		}

		fullUpdateThread = new Thread() {

			@Override
			public void run() {
				LOG.info("Cluster {} Full Update API Start", name);
				while (running) {
					for (QCServer server:serverMap.values()) {
						server.updateFull();
					}
					try {
						TimeUnit.MILLISECONDS.sleep(fullUpdateInterval);
					} catch (InterruptedException e) {
						break;
					}
				}
				LOG.info("Cluster {} Full Update API Stop", name);
			}
		};
		fullUpdateThread.start();

		partialUpdateThread = new Thread() {

			@Override
			public void run() {
				LOG.info("Cluster {} Partial Update API Start", name);
				while (running) {
					for (QCServer server:serverMap.values()) {
						server.updatePartial();
					}
					try {
						TimeUnit.MILLISECONDS.sleep(partialUpdateInterval);
					} catch (InterruptedException e) {
						break;
					}
				}
				LOG.info("Cluster {} Partial Update API Stop", name);
			}
		};
		partialUpdateThread.start();
		
		histogramTimer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				int count = 0;
				for (QCServer server:serverMap.values()) {
					count += server.getAndResetQueryCount();
				}
				histogram.update(count);				
			}
		}, 60*1000, 60*1000);
	}

	public void close() {
		running = false;
		if (eventThread != null) {
			eventThread.interrupt();
		}
		if (fullUpdateThread != null) {
			fullUpdateThread.interrupt();
		}
		if (partialUpdateThread != null) {
			partialUpdateThread.interrupt();
		}
		histogramTimer.cancel();

		for (QCServer server:serverMap.values()) {
			server.stop();
		}
	}

	public void putSystemStats(String server, QCApiServerSystemStats stat) {
		systemStatMap.put(server, stat);
	}

	public List<ClusterServerSysStats> getServerInfo() {
		List<ClusterServerSysStats> list = new ArrayList<>(serverMap.size());
		for (QCServer server:serverMap.values()) {
			list.add(new ClusterServerSysStats(server.getName(), systemStatMap.get(server.getName())));
		}
		Collections.sort(list, new Comparator<ClusterServerSysStats>() {
			@Override
			public int compare(ClusterServerSysStats o1, ClusterServerSysStats o2) {
				return o1.getServer().compareTo(o2.getServer());
			}
		});
		return list;
	}

	public void putObjectPools(String server, QCObjectPoolProfile info) {
		objectPoolMap.put(server, info);
	}

	public void putConnections(String server, List<QCApiConDesc> connections) {
		connectionMap.put(server, connections);
	}

	public List<ClusterServerPoolInfo> getPoolInfo() {
		List<ClusterServerPoolInfo> list = new ArrayList<>(serverMap.size());
		for (QCServer server:serverMap.values()) {
			list.add(new ClusterServerPoolInfo(server.getName(),
					objectPoolMap.get(server.getName()),
					connectionMap.get(server.getName())));
		}
		Collections.sort(list, new Comparator<ClusterServerPoolInfo>() {
			@Override
			public int compare(ClusterServerPoolInfo o1, ClusterServerPoolInfo o2) {
				return o1.getServer().compareTo(o2.getServer());
			}
		});
		return list;
	}

	public QueryProfile getRunningQuery(String clusterUniqueId) {
		return runningMap.get(clusterUniqueId);
	}

	public void addRunningQueries(List<QueryProfile> profileList) {
		synchronized(runningMap) {
			if (profileList != null && profileList.size()>0) {
				String hostname = profileList.get(0).getServer();
				int port = profileList.get(0).getPort();
				Iterator<Entry<String, QueryProfile>> iter = runningMap.entrySet().iterator();
				while (iter.hasNext()) {
					QueryProfile profile = iter.next().getValue();
					if (profile.getServer().equals(hostname) && profile.getPort() == port) {
						iter.remove();
					}
				}
				for (QueryProfile profile:profileList) {
					runningMap.put(profile.getCuqId(), profile);
				}
			}
		}
	}

	public List<QueryProfile> getRunningQueries() {
		List<QueryProfile> list = new ArrayList<>(runningMap.values());
		Collections.sort(list, new Comparator<QueryProfile>() {
			@Override
			public int compare(QueryProfile o1, QueryProfile o2) {
				return Long.compare(o2.getStartTime(), o1.getStartTime());
			}
		});
		return list;
	}

	public void addCompleteQueries(List<QueryProfile> profileList){
		synchronized(completeList) {
			for (QueryProfile profile:profileList) {
				if (completeList.size() > MAX_COMPLETE_QUERIES) {
					completeList.removeFirst();
				}
				runningMap.remove(profile.getCuqId());
				completeList.add(profile);
			}
		}
	}

	public List<QueryProfile> getCompleteQueries() {
		return new ArrayList<>(completeList);
	}

	public void addSubscriber(long idx, CMQCWebSocket ws) {
		if (eventManager != null) {
			eventManager.addSubscriber(idx, ws);
		}
	}

	public void removeSubscriber(long idx) {
		if (eventManager != null) {
			eventManager.removeSubscriber(idx);
		}
	}

	public void processWebSocketEvent(List<QueryProfile> profileList, List<ChangedProfile> changedList) {
		synchronized(runningMap) {
			for (QueryProfile profile:profileList) {
				if (!runningMap.containsKey(profile.getCuqId())){
					runningMap.put(profile.getCuqId(), profile);
					eventManager.add(profile);
				}
			}
			for (ChangedProfile changed:changedList) {
				QueryProfile profile = runningMap.get(changed.getCuqId());
				if (profile != null) {
					profile.setState(changed.getState());
					profile.setRowCnt(changed.getRowCnt());
					profile.setEndTime(changed.getEndTime());
					if (changed.getEndTime()>0) {
						runningMap.remove(changed.getCuqId());
						completeList.add(profile);
						eventManager.add(profile);
					} else {
						eventManager.add(changed);
					}
				}
			}
		}
	}

	private class EventManager {

		private Map<Long, CMQCWebSocket> eventSubscriberMap = new HashMap<>();
		private ArrayDeque<QueryProfile> profileQueueList = new ArrayDeque<>();
		private ArrayDeque<ChangedProfile> changedQueueList = new ArrayDeque<>();
		private int subscriberCount;
		private List<QueryProfile> profileList = new ArrayList<>();
		private List<ChangedProfile> changedList = new ArrayList<>();
		private int count = 0;
		private QueryProfile profile = null;
		private ChangedProfile changed = null;
		private CMQCWebSocket.Response resp = new CMQCWebSocket.Response(CMQCWebSocket.Response.DATA);
		private String respMessage = null;

		public void add(QueryProfile profile) {
			if (profileQueueList.size()+changedQueueList.size() < ConfigKeys.EVENT_QUEUE_MAX_LIMIT && running) {
				profileQueueList.add(profile);
			}
		}

		public void add(ChangedProfile changed) {
			if (profileQueueList.size()+changedQueueList.size() < ConfigKeys.EVENT_QUEUE_MAX_LIMIT && running) {
				changedQueueList.add(changed);
			}
		}

		public void send() {
			count = 0;
			while (profileQueueList.size() > 0 && count++<1000) {
				profile = profileQueueList.removeFirst();
				profileList.add(profile);
			}
			while (changedQueueList.size() > 0 && count++<1000) {
				changed = changedQueueList.removeFirst();
				changedList.add(changed);
			}
			if (profileList.size() > 0 || changedList.size() > 0) {
				synchronized(eventSubscriberMap) {
					if (eventSubscriberMap.size() > 0) {
						resp.setProfiles(profileList);
						resp.setCprofiles(changedList);
						respMessage = gson.toJson(resp);

						for (CMQCWebSocket ws: eventSubscriberMap.values()) {
							try {
								ws.send(respMessage);
							} catch (Exception e) {
								LOG.error("send event error={}, client={}", SQLUtil.getMessage(e), ws.getClient(), e);
							}
						}
					}
				}
				profileList.clear();
				changedList.clear();
			}
		}

		public void addSubscriber(long idx, CMQCWebSocket ws) {
			synchronized (eventSubscriberMap) {
				eventSubscriberMap.put(idx, ws);
				subscriberCount = eventSubscriberMap.size();
			}
		}

		public void removeSubscriber(long idx) {
			synchronized (eventSubscriberMap) {
				eventSubscriberMap.remove(idx);
				subscriberCount = eventSubscriberMap.size();
				if (subscriberCount == 0) {
					profileQueueList.clear();
					changedQueueList.clear();
				}
			}
		}
	}
}
