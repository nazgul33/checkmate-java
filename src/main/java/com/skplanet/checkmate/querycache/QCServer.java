package com.skplanet.checkmate.querycache;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.utils.HttpUtil;

import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCServer {
    private static final boolean DEBUG = false;
    private static final Logger LOG = LoggerFactory.getLogger("querycache");
    private static final int MAX_COMPLETE_QUERIES = 100;
    private final Histogram histogram;

    // inner-classes below are for gson conversion from json object.
    // NOTICE!
    //   sync between QCWebApiServlet
    public static class ConDesc {
        public String driver;
        public int free;
        public int using;
    }

    public static class RuntimeInfo {
        public int nProcessors;
        public long memFree;
        public long memTotal;
        public long memMax;
    }

    public static class ThreadInfo {
        public int webServerThreads;
        public int webServerThreadsIdle;
        public int handlerThreads;
        public int handlerThreadsIdle;
        public int totalThreads;
    }

    public static class SystemInfo {
        public double loadSystem;
        public double loadProcess;
        public long memPhysFree;
        public long memPhysTotal;
        public long swapFree;
        public long swapTotal;
    }

    public static class ObjectPool {
        // configuration
        public long sReloadCycle = 0;
        public int  sMaxPoolSize = 0;
        public float sReloadingThreshold = 0;
        public int sCellCoeff = 0;
        public int[] poolSize = {0, 0, 0, 0};
    }

    public static class Queries {
        public List<QCQuery.QueryImport> runningQueries = new ArrayList<>();
        public List<QCQuery.QueryImport> completeQueries = new ArrayList<>();
    }

    public static class SysInfoCollection {
        public RuntimeInfo jvm;
        public SystemInfo system;
        public ThreadInfo threads;
    }

    public final String name;
    private Map<String, QCQuery> queriesRunning;
    private Map<String, QCQuery> queriesComplete;
    public final QCCluster cluster;

    private List<ConDesc> connections = null;
    private ObjectPool objectPool = null;
    private SysInfoCollection sysInfoCollection = null;

    private long lastQueryUpdate = 0;

    private boolean online;
    private String lastException;

    private AtomicInteger queryCount = new AtomicInteger(0);

    QCServer(String name, QCCluster cluster) {
        this.name = name;
        queriesRunning = new HashMap<>();
        queriesComplete = new LinkedHashMap<String, QCQuery>() {
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_COMPLETE_QUERIES;
            }
        };
        online = false;
        this.cluster = cluster;

        connectRealtimeWebSocket();

        StringBuffer sb = new StringBuffer();
        sb.append("queries.").append(cluster.name).append('.').append(this.name);
        histogram = new Histogram( new SlidingTimeWindowReservoir(5L, TimeUnit.MINUTES) );
        CheckMateServer.getMetrics().register(sb.toString(), histogram);
    }

    public int updateQueryCount() {
        int count = queryCount.getAndSet(0);
        histogram.update(count);
        return count;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getLastException() {
        return lastException;
    }

    public void setLastException(Exception e) {
        this.lastException = e.getMessage();
    }

    public Collection<QCQuery> getRunningQueries() {
        Collection<QCQuery> ql;
        synchronized (queriesRunning) {
            ql = queriesRunning.values();
        }
        return ql;
    }

    public Collection<QCQuery> getCompleteQueries() {
        Collection<QCQuery> ql;
        synchronized (queriesComplete) {
            ql = queriesComplete.values();
        }
        return ql;
    }

    public void Update() {
        // update routines below will set offline when update is failed.
        this.setOnline(true);

        UpdateQueries();
        lastQueryUpdate = new Date().getTime();
        UpdateConnections();
        UpdateObjectPools();
        UpdateSystem();
    }

    protected String getUrl(String path) {
        return "http://" + this.name + ":" + cluster.getOpt().webPort + path;
    }

    protected void processAddQueryEvent(QCQuery q) {
        QCQuery qExisting;
        synchronized (queriesRunning) {
            qExisting = queriesRunning.get(q.cuqId);
            if (qExisting != null) {
                if( !qExisting.Update(q) ) {
                    // not updated : do nothing
                    return;
                }
            } else {
                this.queriesRunning.put(q.cuqId, q);
                queryCount.incrementAndGet();
            }
        }
        this.cluster.addExportedRunningQuery(q);
    }

    protected void processRemoveQueryEvent(QCQuery q) {
        synchronized (queriesRunning) {
            this.queriesRunning.remove(q.cuqId);
        }
        this.cluster.removeExportedRunningQuery(q);
    }

    protected void UpdateQueries() {
        HttpUtil httpUtil = new HttpUtil();
        StringBuffer buf = new StringBuffer();
        int res = 0;

        try {
            res = httpUtil.get( getUrl("/api/queries"), buf);
            if (res == 200) {
                Gson gSon = new Gson();
                Queries queries = gSon.fromJson(buf.toString(), Queries.class);
                synchronized (queriesRunning) {
                    int added = 0;
                    int removed = 0;
                    int updated = 0;
                    List<QCQuery> rqList = new ArrayList<>();
                    Map<String, QCQuery> rqMap = new HashMap<>();
                    // process "current" running query list
                    for (QCQuery.QueryImport qi : queries.runningQueries) {
                        // add to map for reverse existence check below.
                        QCQuery q = new QCQuery(this, qi);
                        rqList.add(q);
                        rqMap.put(q.cuqId, q);
                    }

                    // cross check queries in my list to remove "disappeared" queries.
                    Iterator<Map.Entry<String, QCQuery>> it = queriesRunning.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, QCQuery> entry = it.next();
                        if (!rqMap.containsKey(entry.getKey())) {
                            this.cluster.removeExportedRunningQuery(entry.getValue());
                            it.remove();
                            removed++;
                        }
                    }

                    for (QCQuery q : rqList) {
                        QCQuery qExisting = queriesRunning.get(q.cuqId);
                        if (qExisting != null) {
                            if ( qExisting.Update(q) ) {
                                updated++;
                                this.cluster.addExportedRunningQuery(qExisting);
                            }
                        } else {
                            this.queriesRunning.put(q.cuqId, q);
                            added++;
                            this.cluster.addExportedRunningQuery(q);
                            queryCount.incrementAndGet();
                        }
                    }

                    if (DEBUG) {
                        LOG.debug(this.name + ": running queries +" + added + " #" + updated + " -" + removed);
                    }
                }

                synchronized (queriesComplete) {
                    int added = 0;

                    // process "current" complete query list
                    // sort key endTime, ascending
                    Collections.sort(queries.completeQueries, new Comparator<QCQuery.QueryImport>() {
                        @Override
                        public int compare(QCQuery.QueryImport o1, QCQuery.QueryImport o2) {
                            return (o2.endTime > o1.endTime) ? 1 : (o2.endTime == o1.endTime) ? 0 : -1;
                        }
                    });

                    for (QCQuery.QueryImport qi : queries.completeQueries) {
                        String cuqId = QCQuery.getCuqId(this, qi);
                        // complete query has no changes. process new complete queries only.
                        if (!this.queriesComplete.containsKey(cuqId)) {
                            QCQuery q = new QCQuery(this, qi);
                            this.queriesComplete.put(cuqId, q);
                            cluster.queueCompleteQuery(q);
                            added++;
                        }
                    }

                    if (DEBUG) {
                        LOG.debug("{} : cq size = {}(+{})",
                            this.name, this.queriesComplete.size(), added);
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof ConnectException) {
                LOG.error(this.name + ": updating queries: connection refused.");
                this.setOnline(false);
                this.setLastException(e);
            }
            else {
                LOG.error(this.name + ": updating queries: ", e);
                this.setLastException(e);
            }
        }
    }

    protected void UpdateConnections() {
        HttpUtil httpUtil = new HttpUtil();
        StringBuffer buf = new StringBuffer();
        int res = 0;
        try {
            res = httpUtil.get( getUrl("/api/connections"), buf);
            if (res == 200) {
                Gson gSon = new Gson();
                connections = gSon.fromJson(buf.toString(), new TypeToken<ArrayList<ConDesc>>() {}.getType());
                if (DEBUG) {
                    for (ConDesc con: connections) {
                        LOG.debug(this.name + " con " + con.driver + " free " + con.free + " using " + con.using);
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof ConnectException) {
                LOG.error(this.name + ": updating connections: connection refused.");
                this.setOnline(false);
                this.setLastException(e);
            }
            else {
                LOG.error(this.name + ": updating connections,", e);
                this.setLastException(e);
            }
        }
    }

    protected void UpdateObjectPools() {
        HttpUtil httpUtil = new HttpUtil();
        StringBuffer buf = new StringBuffer();
        int res = 0;
        try {
            res = httpUtil.get( getUrl("/api/objectpool"), buf);
            if (res == 200) {
                Gson gSon = new Gson();
                objectPool = gSon.fromJson(buf.toString(), ObjectPool.class);
                if (DEBUG) {
                    LOG.debug(this.name + " pool " + objectPool.poolSize[0] + "," +
                            objectPool.poolSize[1] + "," +
                            objectPool.poolSize[2] + "," +
                            objectPool.poolSize[3]);
                }
            }
        } catch (Exception e) {
            if (e instanceof ConnectException) {
                LOG.error(this.name + ": updating pools: connection refused.");
                this.setOnline(false);
                this.setLastException(e);
            }
            else {
                LOG.error(this.name + ": updating pools,", e);
                this.setLastException(e);
            }
        }
    }

    protected void UpdateSystem() {
        HttpUtil httpUtil = new HttpUtil();
        StringBuffer buf = new StringBuffer();
        int res = 0;
        try {
            res = httpUtil.get( getUrl("/api/system"), buf);
            if (res == 200) {
                Gson gSon = new Gson();
                sysInfoCollection = gSon.fromJson(buf.toString(), SysInfoCollection.class);
                if (DEBUG) {
                    LOG.debug(this.name + " total threads " + sysInfoCollection.threads.totalThreads);
                    LOG.debug(this.name + " runtime mem used " + (sysInfoCollection.jvm.memTotal - sysInfoCollection.jvm.memFree));
                }
            }
        } catch (Exception e) {
            if (e instanceof ConnectException) {
                LOG.error(this.name + ": updating system stats: connection refused.");
                this.setOnline(false);
                this.setLastException(e);
            }
            else {
                LOG.error(this.name + ": updating system stats,", e);
                this.setLastException(e);
            }
        }
    }

    public Collection<QCQuery.QueryExport> exportRunningQueries() {
        List<QCQuery.QueryExport> ql;
        synchronized (queriesRunning) {
            ql = new ArrayList<>(queriesRunning.size());
            for(QCQuery q: queriesRunning.values()) {
                ql.add( q.export() );
            }
        }
        return ql;
    }

    public Collection<QCQuery.QueryExport> exportCompleteQueries() {
        List<QCQuery.QueryExport> ql;
        synchronized (queriesComplete) {
            ql = new ArrayList<>(queriesComplete.size());
            for(QCQuery q: queriesComplete.values()) {
                ql.add( q.export() );
            }
        }
        return ql;
    }

    public SysInfoCollection getSysInfoCollection() {
        return sysInfoCollection;
    }

    public ObjectPool getObjectPool() {
        return objectPool;
    }

    public List<ConDesc> getConnections() {
        return connections;
    }

    private QCClientWebSocket rtWebSocket = null;
    public QCClientWebSocket getRtWebSocket() {
        return rtWebSocket;
    }
    public void removeRtWebSocket() {
        rtWebSocket = null;
    }
    public void connectRealtimeWebSocket() {
        String uri = "ws://" + this.name + ":" + cluster.getOpt().webPort + "/api/websocket";
        WebSocketClient c = new WebSocketClient();
        QCClientWebSocket s = new QCClientWebSocket(this);
        rtWebSocket = null;
        try {
            c.start();
            URI rtUri = new URI(uri);
            ClientUpgradeRequest req = new ClientUpgradeRequest();
            c.connect(s, rtUri, req);
            rtWebSocket = s;
        } catch (URISyntaxException e) {
            LOG.error("invalid uri " + uri, e);
        } catch (Exception e) {
            LOG.error("websocket client error", e);
        }
    }
}
