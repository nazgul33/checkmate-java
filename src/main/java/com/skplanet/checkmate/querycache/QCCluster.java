package com.skplanet.checkmate.querycache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCCluster {
    private static final Logger LOG = LoggerFactory.getLogger("querycache");

    static public class Options {
        public int webPort = 8080;
        public int partialUpdateInterval = 3000;
        public int fullUpdateInterval = 10000;
        public String[] servers;
    }

    public final String name;
    private Map<String, QCServer> servers;
    private Options opt;
    private long lastUpdate = 0;
    ConcurrentLinkedQueue<AsyncContext> asyncRQList = new ConcurrentLinkedQueue<>();

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
        if (servers.containsKey(name)) {
            return servers.get(name);
        }
        return null;
    }

    public void Update(boolean partial) {
        // update all servers
        boolean updated = false;
        Date start = new Date();
        for (Map.Entry<String, QCServer> entry : servers.entrySet()) {
            QCServer server = entry.getValue();
            if (server.Update(partial)) {
                updated = true;
            }

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
        LOG.debug("Updating cluster " + name + " took " + (end.getTime() - start.getTime()) + " ms" + (partial? " (partial)":""));
        if (updated) {
            lastUpdate = new Date().getTime();
            // dispatch queued async jobs.
            for (AsyncContext async: asyncRQList) {
                async.dispatch();
            }
            asyncRQList.clear();
        }
    }

    public long getLastUpdateTime() {
        return lastUpdate;
    }

    public Options getOpt() {
        return opt;
    }

    public Collection<QCQuery.QueryExport> exportRunningQueries() {
        List<QCQuery.QueryExport> ql = new ArrayList<>();
        for (QCServer s: servers.values()) {
            ql.addAll(s.exportRunningQueries());
        }
        Collections.sort(ql, new Comparator<QCQuery.QueryExport>() {
            @Override
            public int compare(QCQuery.QueryExport o1, QCQuery.QueryExport o2) {
                return ((o2.startTime - o1.startTime) < 0) ? -1 : (o2.startTime == o1.startTime) ? 0 : 1;
            }
        });
        return ql;
    }

    public Collection<QCQuery.QueryExport> exportCompleteQueries() {
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
        return ql;
    }

    public void addAsyncContextRQ(AsyncContext async) {
        asyncRQList.add(async);
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
}