package com.skplanet.checkmate.querycache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.skplanet.checkmate.utils.HttpUtil;

import java.net.ConnectException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCServer {
    private static final boolean DEBUG = false;
    private static final Logger LOG = LoggerFactory.getLogger("querycache");
    private static final int MAX_COMPLETE_QUERIES = 100;

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
    private boolean online;
    public final QCCluster cluster;

    private List<ConDesc> connections = null;
    private ObjectPool objectPool = null;
    private SysInfoCollection sysInfoCollection = null;

    private long lastQueryUpdate = 0;

    QCServer(String name, QCCluster cluster) {
        this.name = name;
        queriesRunning = new HashMap<>();
        queriesComplete = new HashMap<>();
        online = false;
        this.cluster = cluster;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
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

    public boolean Update(boolean partial) {
        if (partial) {
            if (this.queriesRunning.size() > 0) {
                UpdateQueries(true);
                lastQueryUpdate = new Date().getTime();
                return true;
            }
            return false;
        }

        UpdateQueries(false);
        lastQueryUpdate = new Date().getTime();
        UpdateConnections();
        UpdateObjectPools();
        UpdateSystem();
        return true;
    }

    protected String getUrl(String path) {
        return "http://" + this.name + ":" + cluster.getOpt().webPort + path;
    }

    protected void UpdateQueries(boolean runningQueriesOnly) {
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
                    Map<String, QCQuery.QueryImport> rqMap = new HashMap<>();
                    // process "current" running query list
                    for (QCQuery.QueryImport q : queries.runningQueries) {
                        // add to map for reverse existence check below.
                        rqMap.put(q.queryId, q);
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

                    for (QCQuery.QueryImport q : queries.runningQueries) {
                        q.queryId = q.queryId.toLowerCase();
                        if (this.queriesRunning.containsKey(q.queryId)) {
                            QCQuery qExisting = this.queriesRunning.get(q.queryId);
                            if ( qExisting.Update(q) ) {
                                updated++;
                                this.cluster.addExportedRunningQuery(qExisting);
                            }
                        } else {
                            QCQuery qNew = new QCQuery(this, q);
                            this.queriesRunning.put(q.queryId, qNew);
                            added++;
                            this.cluster.addExportedRunningQuery(qNew);
                        }
                    }

                    if (DEBUG) {
                        LOG.debug(this.name + ": running queries +" + added + " #" + updated + " -" + removed);
                    }
                }
                if (runningQueriesOnly == false) {
                    synchronized (queriesComplete) {
                        int added = 0;
                        int removed = 0;
                        // process "current" complete query list
                        for (QCQuery.QueryImport q : queries.completeQueries) {
                            q.queryId = q.queryId.toLowerCase();
                            // complete query has no changes. process new complete queries only.
                            if (!this.queriesComplete.containsKey(q.queryId)) {
                                QCQuery qNew = new QCQuery(this, q);
                                this.queriesComplete.put(q.queryId, qNew);
                                added++;
                            }
                        }
                        // remove oldest query. sort key is endTime
                        if (this.queriesComplete.size() > MAX_COMPLETE_QUERIES) ;
                        {
                            ArrayList<QCQuery> cqList = new ArrayList<>(queriesComplete.size());
                            cqList.addAll(queriesComplete.values());
                            Collections.sort(cqList, new Comparator<QCQuery>() {
                                @Override
                                public int compare(QCQuery o1, QCQuery o2) {
                                    return ((o2.startTime - o1.startTime) < 0) ? -1 : (o2.startTime == o1.startTime) ? 0 : 1;
                                }
                            });
                            for (int i = cqList.size()-1; i >= MAX_COMPLETE_QUERIES; i--) {
                                queriesComplete.remove(cqList.get(i).id);
                                removed++;
                            }
                        }
                        if (DEBUG) {
                            LOG.debug(this.name + ": complete queries +" + added + " -" + removed);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof ConnectException)
                LOG.error(this.name + ": updating queries: connection refused.");
            else
                LOG.error(this.name + ": updating queries: ", e);
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
}
