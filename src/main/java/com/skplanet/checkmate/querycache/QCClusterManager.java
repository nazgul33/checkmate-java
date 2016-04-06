package com.skplanet.checkmate.querycache;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCClusterManager {
    private static final Logger LOG = LoggerFactory.getLogger("querycache");
    private static QCClusterManager qcMgr = null;

    private Map<String, QCCluster.Options> clusterConfigMap = new HashMap<>();

    private int fullUpdateInterval = 10 * 1000;
    private int pingWebSocketsInterval = 5 * 1000;

    private void readConfiguration() {
        String home = System.getenv("CM_HOME");
        if (home == null) {
            home = "./";
        }
        String conf = home + "/conf/querycache.ini";
        HierarchicalINIConfiguration clusterConfigFile = null;
        String[] clusterNames;
        QCCluster.Options options[];

        try {
            LOG.info("reading checkmate::querycache configuration from " + conf);
            clusterConfigFile = new HierarchicalINIConfiguration(conf);
            SubnodeConfiguration globalSection = clusterConfigFile.getSection(null);
            globalSection.setThrowExceptionOnMissing(true);
            clusterNames = globalSection.getStringArray("Clusters");
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

                QCClusterManager.qcMgr.UpdateClusters();
                lastFullUpdate = new Date();
                QCClusterThread.this.fullUpdateTaskRunning.set(false);
            }
        }

        private class QCClusterWebSocketPingTask extends TimerTask {
            @Override
            public void run() {
                QCClusterManager.qcMgr.pingWebSockets();
            }
        }

        private class QCCounterUpdateTask extends TimerTask {
            @Override
            public void run() {
                for (QCCluster cluster: clusters) {
                    cluster.updateCounters();
                }
            }
        }
        TimerTask qcTimerFull = new QCClusterFullUpdateTask();
        TimerTask qcTimerPingWebSockets = new QCClusterWebSocketPingTask();
        TimerTask qcCounterUpdateTask = new QCCounterUpdateTask();

        AtomicBoolean fullUpdateTaskRunning = new AtomicBoolean(false);
        Date lastFullUpdate = new Date();

        @Override
        public void run() {
            Timer timerFull = new Timer(true);
            Timer timerPingWebSockets = new Timer(true);
            Timer timerCounterUpdateTask = new Timer(true);

            timerFull.scheduleAtFixedRate(qcTimerFull, 0, fullUpdateInterval);
            timerPingWebSockets.scheduleAtFixedRate(qcTimerPingWebSockets, 0, pingWebSocketsInterval);
            timerCounterUpdateTask.scheduleAtFixedRate(qcCounterUpdateTask, 60*1000, 60*1000);
            while (!QCClusterManager.this.quitThread) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                }
            }

            timerFull.cancel();
            timerPingWebSockets.cancel();
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
    public void UpdateClusters() {
        for (QCCluster cluster: clusters) {
            cluster.Update();
        }
    }

    public QCCluster getCluster(String clusterName) {
        for (QCCluster c: clusters) {
            if (c.name.equals(clusterName)) {
                return c;
            }
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

    private void pingWebSockets() {
        for (QCCluster cluster: clusters) {
            for (QCServer server: cluster.getServers().values()) {
                QCClientWebSocket ws = server.getRtWebSocket();
                if (ws == null) {
                    server.connectRealtimeWebSocket();
                }
                else {
                    ws.ping();
                }
            }
        }
    }
}
