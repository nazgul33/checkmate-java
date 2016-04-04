package com.skplanet.checkmate.querycache;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.google.gson.Gson;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.servlet.CMQCServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCCluster {
    private static final boolean DEBUG = false;
    private static final Logger LOG = LoggerFactory.getLogger("querycache");
    private static int MAX_COMPLETE_QUERIES = 100;
    private static String RQE_ADDED = "runningQueryAdded";
    private static String RQE_UPDATED = "runningQueryUpdated";
    private static String RQE_REMOVED = "runningQueryRemoved";

    static public class Options {
        public int webPort = 8080;
        public int partialUpdateInterval = 3000;
        public int fullUpdateInterval = 10000;
        public String[] servers;
    }

    public final String name;
    private Map<String, QCServer> servers;
    private Options opt;

    private Timer nofifyTimer = new Timer();
    Histogram histogram;

    public QCCluster(String name, Options opt) {
        this.name = name;
        this.opt = opt;

        servers = new HashMap<>();
        for (String serverName : opt.servers) {
            if (servers.containsKey(serverName)) {
                System.console().printf("duplicate server name %s\n", serverName);
                System.exit(1);
            }
            servers.put(serverName, new QCServer(serverName, this));
        }

        TimerTask wsNotifyTask = new TimerTask() {
            @Override
            public void run() {
                notifySubscribers();
            }
        };
        nofifyTimer.scheduleAtFixedRate(wsNotifyTask, 0, 50);

        histogram = new Histogram( new SlidingTimeWindowReservoir(5L, TimeUnit.MINUTES) );
        CheckMateServer.getMetrics().register("queries."+name, histogram);
    }

    public void updateCounters() {
        int count = 0;
        for (Map.Entry<String, QCServer> entry : servers.entrySet()) {
            count += entry.getValue().updateQueryCount();
        }
        histogram.update(count);
    }
    public Map<String, QCServer> getServers() {
        return servers;
    }

    public QCServer getServer(String name) {
        return servers.get(name);
    }

    public void Update() {
        // update all servers
        Date start = new Date();
        for (Map.Entry<String, QCServer> entry : servers.entrySet()) {
            QCServer server = entry.getValue();
            server.Update();

            // update sysinfo
            QCServer.SysInfoCollection sysInfo = server.getSysInfoCollection();
            if (sysInfo != null) {
                SystemInfo mySysInfo = sysInfoMap.get(server.name);
                if ( mySysInfo == null) {
                    mySysInfo = new SystemInfo();
                    mySysInfo.server = server.name;
                    sysInfoMap.put(server.name, mySysInfo);
                }
                mySysInfo.jvm = sysInfo.jvm;
                mySysInfo.system = sysInfo.system;
                mySysInfo.threads = sysInfo.threads;
            }

            QCServer.ObjectPool objPoolInfo = server.getObjectPool();
            Collection<QCServer.ConDesc> connPoolInfo = server.getConnections();
            if (objPoolInfo != null && connPoolInfo != null) {
                PoolInfo myPoolInfo = poolInfoMap.get(server.name);
                if ( myPoolInfo == null) {
                    myPoolInfo = new PoolInfo();
                    myPoolInfo.server = server.name;
                    poolInfoMap.put(server.name, myPoolInfo);
                }
                myPoolInfo.objPool = objPoolInfo;
                myPoolInfo.connPoolList = connPoolInfo;
            }
        }
        Date end = new Date();
        if (DEBUG) {
            LOG.debug("Updating cluster " + name + " took " + (end.getTime() - start.getTime()) + " ms");
        }

        refreshExportedCompleteQueries();
    }

    public Options getOpt() {
        return opt;
    }

    private static class RunningQueryEvent {
        public long timestamp = System.currentTimeMillis();
        public String msgType = null;
        public QCQuery.QueryExport query = null;
        public String cuqId = null;
    }

    private Map<String, QCQuery.QueryExport> exportedRunningQueriesMap = new HashMap<>();
    public QCQuery.QueryExport getExportedRunningQueryByCuqId(String cuqId) {
        QCQuery.QueryExport q;
        synchronized (exportedRunningQueriesMap) {
            q = exportedRunningQueriesMap.get(cuqId);
        }
        return q;
    }

    public void addExportedRunningQuery(QCQuery q) {
        QCQuery.QueryExport eq = q.export();
        boolean queryUpdated;
        synchronized (exportedRunningQueriesMap) {
            queryUpdated = (exportedRunningQueriesMap.put(eq.cuqId, eq) != null);
        }

        RunningQueryEvent evt = new RunningQueryEvent();
        if (queryUpdated) {
            evt.msgType = RQE_UPDATED;
            evt.query = eq;
            evt.cuqId = null;
        } else {
            evt.msgType = RQE_ADDED;
            evt.query = eq;
            evt.cuqId = null;
        }
        addRunningQueryEvent(evt);
    }

    public void removeExportedRunningQuery(QCQuery q) {
        boolean notifyClients;
        synchronized (exportedRunningQueriesMap) {
            if (!exportedRunningQueriesMap.containsKey(q.cuqId)) {
                LOG.error("trying to remove non-existent cuqId");
            }
            notifyClients = (exportedRunningQueriesMap.remove(q.cuqId) != null);
        }

        if (notifyClients) {
            RunningQueryEvent evt = new RunningQueryEvent();
            evt.msgType = RQE_REMOVED;
            evt.query = null;
            evt.cuqId = q.cuqId;
            addRunningQueryEvent(evt);
        }
    }

    public Collection<QCQuery.QueryExport> getExportedRunningQueries() {
        List<QCQuery.QueryExport> ql = new ArrayList<>();
        synchronized (exportedRunningQueriesMap) {
            ql.addAll(exportedRunningQueriesMap.values());
        }
        Collections.sort(ql, new Comparator<QCQuery.QueryExport>() {
            @Override
            public int compare(QCQuery.QueryExport o1, QCQuery.QueryExport o2) {
                return ((o1.startTime - o2.startTime) < 0) ? -1 : (o1.startTime == o2.startTime) ? 0 : 1;
            }
        });
        return ql;
    }

    // complete query update accelerators
    private List<QCQuery> tmpCompleteQueries = new ArrayList<>();
    void queueCompleteQuery(QCQuery q) {
        synchronized (tmpCompleteQueries) {
            tmpCompleteQueries.add(q);
        }
    }

    private LinkedList<QCQuery.QueryExport> exportedCompleteQueries = new LinkedList<>();
    private void refreshExportedCompleteQueries() {
        synchronized (tmpCompleteQueries) {
            Collections.sort(tmpCompleteQueries, new Comparator<QCQuery>() {
                @Override
                public int compare(QCQuery o1, QCQuery o2) {
                    return (o2.endTime > o1.endTime) ? 1 : (o2.endTime == o1.endTime) ? 0 : -1;
                }
            });

            synchronized (exportedCompleteQueries) {
                for (QCQuery q: tmpCompleteQueries) {
                    // addFirst() to make most recent query comes first.
                    exportedCompleteQueries.addFirst(q.export());
                }

                // maintain size of exportedCompleteQueries
                int toRemove = exportedCompleteQueries.size() - MAX_COMPLETE_QUERIES;
                if (toRemove > 0) {
                    for (int i=0; i<toRemove; i++) {
                        exportedCompleteQueries.removeLast();
                    }
                }
            }
            tmpCompleteQueries.clear();
        }
    }

    // TODO: getting subset of complete queries
    public Collection<QCQuery.QueryExport> getExportedCompleteQueries() {
        List<QCQuery.QueryExport> ql = new ArrayList<>();
        synchronized (exportedCompleteQueries) {
            ql.addAll(exportedCompleteQueries);
        }
        return ql;
    }

    static public class SystemInfo {
        public String server;
        public QCServer.RuntimeInfo jvm;
        public QCServer.SystemInfo system;
        public QCServer.ThreadInfo threads;
    }
    private Map<String, SystemInfo> sysInfoMap = new HashMap<>();
    public Collection<SystemInfo> getSystemInfo() {
        List<SystemInfo> l = new ArrayList<>();
        synchronized (sysInfoMap) {
            l.addAll(sysInfoMap.values());
        }

        Collections.sort(l, new Comparator<SystemInfo>() {
            @Override
            public int compare(SystemInfo o1, SystemInfo o2) {
                return o1.server.compareTo(o2.server);
            }
        });
        return l;
    }

    static public class PoolInfo {
        public String server;
        public QCServer.ObjectPool objPool;
        public Collection<QCServer.ConDesc> connPoolList;
    }

    private Map<String, PoolInfo> poolInfoMap = new HashMap<>();
    public Collection<PoolInfo> getPoolInfo() {
        List<PoolInfo> l = new ArrayList<>();
        synchronized (poolInfoMap) {
            l.addAll(poolInfoMap.values());
        }
        Collections.sort(l, new Comparator<PoolInfo>() {
            @Override
            public int compare(PoolInfo o1, PoolInfo o2) {
                return o1.server.compareTo(o2.server);
            }
        });
        return l;
    }

    private Map<Integer, CMQCServerWebSocket> realtimeMessageReceivers = new HashMap<>();
    AtomicInteger realtimeMessageReceiversId = new AtomicInteger(0);

    public int subscribe(CMQCServerWebSocket ws) {
        int id = realtimeMessageReceiversId.getAndAdd(1);
        realtimeMessageReceivers.put(id, ws);
        return id;
    }

    public void unSubscribe(int id) {
        realtimeMessageReceivers.remove(id);
    }

    private LinkedList<RunningQueryEvent> evtQueue = new LinkedList<>();
    private void addRunningQueryEvent(RunningQueryEvent evt) {
        long deadline = System.currentTimeMillis() - 100; // kill add event after deadline
        synchronized (evtQueue) {
            if (RQE_REMOVED.equals(evt.msgType)) {
                Iterator<RunningQueryEvent> it = evtQueue.iterator();
                boolean removeAllEvt = false;
                while (it.hasNext()) {
                    RunningQueryEvent rqe = it.next();
                    String cuqId = (rqe.cuqId != null)? rqe.cuqId:rqe.query.cuqId;
                    if (removeAllEvt) {
                        if (evt.cuqId.equals(cuqId)) {
                            it.remove();
                        }
                    } else {
                        if (rqe.timestamp >= deadline) {
                            if (evt.cuqId.equals(cuqId) && RQE_ADDED.equals(rqe.msgType)) {
                                it.remove();
                                removeAllEvt = true;
                            }
                        }
                    }
                }
                // if matching add event is not found, add remove evt in queue
                if (!removeAllEvt) {
                    evtQueue.addLast(evt);
                }
            }
            else {
                // other events are just added in queue
                evtQueue.addLast(evt);
            }
        }
    }
    public void notifySubscribers() {
        long deadline = System.currentTimeMillis() - 100; // send events older than 100ms

        LinkedList<RunningQueryEvent> el = new LinkedList<>();
        synchronized (evtQueue) {
            Iterator<RunningQueryEvent> it = evtQueue.iterator();
            while (it.hasNext()) {
                RunningQueryEvent evt = it.next();
                if (evt.timestamp < deadline) {
                    el.addLast(evt);
                    it.remove();
                }
            }
        }

        if (el.size()>0) {
            Gson gson = new Gson();
            Iterator<RunningQueryEvent> it = el.iterator();
            while (it.hasNext()) {
                RunningQueryEvent evt = it.next();
                String msg = gson.toJson(evt);
                for (CMQCServerWebSocket ws : realtimeMessageReceivers.values()) {
                    ws.sendMessage(msg);
                }
            }
            el.clear();
        }
    }
}
