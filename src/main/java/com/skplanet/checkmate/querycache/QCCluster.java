package com.skplanet.checkmate.querycache;

import com.google.gson.Gson;
import com.skplanet.checkmate.servlet.CMQCServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCCluster {
    private static final boolean DEBUG = false;
    private static final Logger LOG = LoggerFactory.getLogger("querycache");
    private static int MAX_COMPLETE_QUERIES = 100;

    static public class Options {
        public int webPort = 8080;
        public int partialUpdateInterval = 3000;
        public int fullUpdateInterval = 10000;
        public String[] servers;
    }

    public final String name;
    private Map<String, QCServer> servers;
    private Options opt;

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
    }

    public Map<String, QCServer> getServers() {
        return servers;
    }

    public QCServer getServer(String name) {
        return servers.get(name);
    }

    public void Update(boolean partial) {
        // update all servers
        Date start = new Date();
        for (Map.Entry<String, QCServer> entry : servers.entrySet()) {
            QCServer server = entry.getValue();
            server.Update(partial);

            // sysinfo update only if it's not partial update
            if (!partial) {
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
        }
        Date end = new Date();
        if (DEBUG) {
            LOG.debug("Updating cluster " + name + " took " + (end.getTime() - start.getTime()) + " ms" + (partial ? " (partial)" : ""));
        }

        refreshExportedCompleteQueries();
    }

    public Options getOpt() {
        return opt;
    }

    private static class RunningQueryEvent {
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
            evt.msgType = "runningQueryUpdated";
            evt.query = eq;
            evt.cuqId = null;
        } else {
            evt.msgType = "runningQueryAdded";
            evt.query = eq;
            evt.cuqId = null;
        }
        notifySubscribers(evt);
    }

    public void removeExportedRunningQuery(QCQuery q) {
        String cuqId = q.getCuqId();
        boolean notifyClients;
        synchronized (exportedRunningQueriesMap) {
            if (!exportedRunningQueriesMap.containsKey(cuqId)) {
                LOG.debug("trying to remove non-existent cuqId");
            }
            notifyClients = (exportedRunningQueriesMap.remove(cuqId) != null);
        }

        if (notifyClients) {
            RunningQueryEvent evt = new RunningQueryEvent();
            evt.msgType = "runningQueryRemoved";
            evt.query = null;
            evt.cuqId = cuqId;
            notifySubscribers(evt);
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

    private List<QCQuery.QueryExport> exportedCompleteQueries = new ArrayList<>();
    private void refreshExportedCompleteQueries() {
        List<QCQuery.QueryExport> ql = new ArrayList<>();
        for (QCServer s: servers.values()) {
            ql.addAll(s.exportCompleteQueries());
        }
        Collections.sort(ql, new Comparator<QCQuery.QueryExport>() {
            @Override
            public int compare(QCQuery.QueryExport o1, QCQuery.QueryExport o2) {
                return ((o2.endTime - o1.endTime) < 0) ? -1 : (o2.endTime == o1.endTime) ? 0 : 1;
            }
        });

        ql = ql.subList(0, (ql.size()>MAX_COMPLETE_QUERIES)? MAX_COMPLETE_QUERIES:ql.size());
        // subset of all queries from servers, to limit number of queries remembered
        synchronized (exportedCompleteQueries) {
            exportedCompleteQueries.clear();
            exportedCompleteQueries.addAll(ql);
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
    public void notifySubscribers( RunningQueryEvent evt ) {
        if ( realtimeMessageReceivers.size() > 0 ) {
            Gson gson = new Gson();
            String msg = gson.toJson(evt);
            for (CMQCServerWebSocket ws: realtimeMessageReceivers.values()) {
                ws.sendMessage(msg);
            }
        }
    }
}
