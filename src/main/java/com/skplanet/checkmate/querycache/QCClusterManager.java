package com.skplanet.checkmate.querycache;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCClusterManager {
    private static final Logger LOG = LoggerFactory.getLogger("querycache");
    private static QCClusterManager qcMgr = null;

    private Map<String, QCCluster.Options> clusterConfigMap = new HashMap<>();

    private int partialUpdateInterval = 3 * 1000;
    private int fullUpdateInterval = 10 * 1000;

    private void readConfiguration() {
        String home = System.getenv("QC_HOME");
        if (home == null) {
            home = "./";
        }
        String conf = home + "/conf/querycache.ini";
        HierarchicalINIConfiguration clusterConfigFile = null;
        String[] clusterNames;
        QCCluster.Options options[];

        try {
            clusterConfigFile = new HierarchicalINIConfiguration(conf);
            SubnodeConfiguration globalSection = clusterConfigFile.getSection(null);
            globalSection.setThrowExceptionOnMissing(true);
            clusterNames = globalSection.getStringArray("Clusters");
            partialUpdateInterval = globalSection.getInt("PartialUpdateInterval");
            fullUpdateInterval = globalSection.getInt("FullUpdateInterval");

            for (String cn: clusterNames) {
                SubnodeConfiguration sc = clusterConfigFile.getSection(cn);
                QCCluster.Options opt = new QCCluster.Options();
                opt.webPort = sc.getInt("WebPort");
                opt.servers = sc.getStringArray("Servers");

                if (clusterConfigFile.containsKey(cn)) {
                    throw new ConfigurationException("duplicate cluster definition");
                }
                clusterConfigMap.put(cn, opt);
            }
        }
        catch (Exception e) {
            LOG.error("error loading configuration.", e);
            System.exit(1);
        }
    }

    public class QCClusterThread implements Runnable {
        private class QCClusterFullUpdateTask extends TimerTask {
            @Override
            public void run() {
                if ( !QCClusterThread.this.fullUpdateTaskRunning.compareAndSet(false, true) ) {
                    // update task is running. skip this time....
                    LOG.warn("a full update task is running. skipping full update task");
                    return;
                }
                int waitCount = 0;
                while ( (!QCClusterThread.this.partialUpdateTaskRunning.compareAndSet(false, true)) && waitCount < 10 ) {
                    // update task is running. skip this time....
                    LOG.warn("a partial update task is running. waiting 0.5 sec");
                    waitCount++;
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                        // exception while sleeping.
                    }
                }
                if (waitCount >= 10 && QCClusterThread.this.partialUpdateTaskRunning.get() == false) {
                    LOG.warn("full update waited to long. skipping");
                    QCClusterThread.this.fullUpdateTaskRunning.set(false);
                    return;
                }

                QCClusterManager.qcMgr.UpdateClusters(false);
                lastFullUpdate = new Date();
                QCClusterThread.this.fullUpdateTaskRunning.set(false);
                QCClusterThread.this.partialUpdateTaskRunning.set(false);
            }
        }
        private class QCClusterPartialUpdateTask extends TimerTask {
            @Override
            public void run() {
                if ( QCClusterThread.this.fullUpdateTaskRunning.get() == true ) {
                    // update task is running. skip this time....
                    LOG.warn("a full update task is running. skipping partial update task");
                    return;
                }
                if ( !QCClusterThread.this.partialUpdateTaskRunning.compareAndSet(false, true) ) {
                    // update task is running. skip this time....
                    LOG.warn("a partial update task is running. skipping partial update task");
                }
                QCClusterManager.qcMgr.UpdateClusters(true);
                lastPartialUpdate = new Date();
                QCClusterThread.this.partialUpdateTaskRunning.set(false);
            }
        }
        TimerTask qcTimerFull = new QCClusterFullUpdateTask();
        TimerTask qcTimerPartial = new QCClusterPartialUpdateTask();
        AtomicBoolean fullUpdateTaskRunning = new AtomicBoolean(false);
        AtomicBoolean partialUpdateTaskRunning = new AtomicBoolean(false);
        Date lastFullUpdate = new Date();
        Date lastPartialUpdate = new Date();

        @Override
        public void run() {
            Timer timerFull = new Timer(true);
            Timer timerPartial = new Timer(true);

            timerFull.scheduleAtFixedRate(qcTimerFull, 0, fullUpdateInterval);
            timerPartial.scheduleAtFixedRate(qcTimerPartial, 0, partialUpdateInterval);

            while (!QCClusterManager.this.quitThread) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                }
            }

            timerFull.cancel();
            timerPartial.cancel();
        }
    }

    public static QCClusterManager getInstance() {
        if (qcMgr == null) {
            qcMgr = new QCClusterManager();
        }

        return qcMgr;
    }

    private List<QCCluster> clusters = new ArrayList<>();
    private Thread qcClusterThread = new Thread(this.new QCClusterThread());
    private boolean quitThread = false;

    protected QCClusterManager() {
        readConfiguration();

        // initialize clusters;
        for (Map.Entry<String, QCCluster.Options> entry: clusterConfigMap.entrySet()) {
            QCCluster cluster = new QCCluster(entry.getKey(), entry.getValue());
            clusters.add(cluster);
        }

        // initialize thread;
        qcClusterThread = new Thread(this.new QCClusterThread());
    }

    public void startThread() {
        qcClusterThread.start();
    }

    // stop
    public void finishThread() {
        quitThread = true;
        try {
            qcClusterThread.join();
        } catch (Exception e) {
            LOG.error("exception while waiting for thread " + e.toString());
        }
    }
    public void UpdateClusters(boolean partial) {
        for (QCCluster cluster: clusters) {
            cluster.Update(partial);
        }
    }

    public QCCluster getCluster(String clusterName) {
        for (QCCluster c: clusters) {
            if (clusterName.equals(c.name)) {
                return c;
            }
        }
        return null;
    }

    public Collection<QCQuery.QueryExport> getExportedRunningQueries(String clusterName) {
        QCCluster c = getCluster(clusterName);
        if (c!=null) {
            return c.getExportedRunningQueries();
        }
        return null;
    }

    public Collection<QCQuery.QueryExport> getExportedCompleteQueries(String clusterName) {
        QCCluster c = getCluster(clusterName);
        if (c!=null) {
            return c.getExportedCompleteQueries();
        }
        return null;
    }

    public long getLastUpdateTime(String clusterName) {
        QCCluster c = getCluster(clusterName);
        if (c!=null) {
            return c.getLastUpdateTime();
        }
        return -1;
    }

    private ConcurrentLinkedQueue<AsyncContext> asyncRQList = new ConcurrentLinkedQueue<>();
    public void addAsyncContextRQ(AsyncContext async, String clusterName) {
        QCCluster c = getCluster(clusterName);
        if (c!=null) {
            c.addAsyncContextRQ(async);
        }
    }

    public Collection<QCCluster.SystemInfo> getSystemInfoList(String clusterName) {
        QCCluster c = getCluster(clusterName);
        if (c!=null) {
            return c.getSystemInfo();
        }
        return null;
    }

    public Collection<QCCluster.PoolInfo> getPoolInfoList(String clusterName) {
        QCCluster c = getCluster(clusterName);
        if (c!=null) {
            return c.getPoolInfo();
        }
        return null;
    }

    public Collection<String> getClusterList() {
        List<String> cl = new ArrayList<>();
        for (QCCluster c: clusters) {
            cl.add(c.name);
        }
        return cl;
    }
}
