package com.skplanet.checkmate;

import static com.skplanet.checkmate.ConfigKeys.*;

import java.io.File;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.checkmate.utils.MailSender;

/**
 * Hello world!
 *
 */
public class CheckMateServer {

    private static final Logger LOG = LoggerFactory.getLogger(CheckMateServer.class);
    
    private static final MetricRegistry metrics = new MetricRegistry();

    private File confDir;
    
    private final HierarchicalINIConfiguration ini;

    private QCClusterManager clusterManager;
    private CheckMateWebServer webServer;

    private static CheckMateServer _this = null;
    
    private CheckMateServer(File homeDir) throws Exception {

    	confDir = new File(homeDir, "conf");

    	File iniFile = new File(confDir, CHECKMATE_INI_FILE);
        LOG.info("reading checkmate configuration from " + iniFile);

        ini = new HierarchicalINIConfiguration(iniFile);
        
        SubnodeConfiguration global = ini.getSection(CHECKMATE_GLOBAL_SECTION);
        global.setThrowExceptionOnMissing(true);
        
        // MailSender
        {
            SubnodeConfiguration email = ini.getSection(CHECKMATE_EMAIL_SECTION);
            email.setThrowExceptionOnMissing(true);
            
            String server = email.getString(CHECKMATE_EMAIL_SMTP_SERVER, null);
            String from   = email.getString(CHECKMATE_EMAIL_SMTP_FROM, null);
            int port      = email.getInt(CHECKMATE_EMAIL_SMTP_PORT, CHECKMATE_EMAIL_STMP_PORT_DEFAULT);
            String tos    = email.getString(CHECKMATE_EMAIL_RECIPIENTS, null);

            if (server != null && !server.isEmpty() && tos != null && !tos.isEmpty()) {
            	MailSender.initialize(server, port, from,  tos.split(","));
            } else {
            	LOG.info("mail sender not initialized");
            }
        }
        
        // ClusterManager
        clusterManager = new QCClusterManager(confDir);
        
        // WebServer
        {
        	int port = global.getInt(CHECKMATE_WEB_PORT, CHECKMATE_WEB_PORT_DEFAULT);
        	boolean useWebSocket = global.getBoolean(CHECKMATE_USER_WEBSOCET, CHECKMATE_USER_WEBSOCET_DEFAULT);
        	webServer = new CheckMateWebServer(port, useWebSocket, homeDir, metrics);
        }
    }
    
    public static CheckMateServer getInstance() {
        return _this;
    }
    
    public static QCClusterManager getClusterManager() {
    	return _this.clusterManager;
    }
    
    public static MetricRegistry getMetrics() {
        return metrics;
    }

    private void startClusterManagers() {
    	
        LOG.info("Starting QueryCache Cluster Thread");
        clusterManager.start();

//        LOG.info("Starting Yarn Monitor");
//        YarnMonitor.getInstance();
    }
    
    private void startWebServer() throws Exception {
    	webServer.runWebServer();
    }
    
    private void stopClusterManagers() {
        LOG.info("Shutting down QueryCache Cluster Thread");
        clusterManager.stop();
    }

    public static void main( String[] args ) {

        String cmHome = System.getenv("CM_HOME");
        if (cmHome == null) {
            throw new RuntimeException("$CM_HOME undefined");
        }
        
        File homeDir = new File(cmHome);
    	try {
    		_this = new CheckMateServer(homeDir);
    		_this.startClusterManagers();
        } catch (Exception e) {
            LOG.error("error initializing CheckMateServer", e);
            System.exit(1);
        }

        try {
        	_this.startWebServer();
        } catch (Exception e) {
            LOG.error("exception while starting web interface", e);
            System.exit(1);
        }
        
        _this.stopClusterManagers();
    }
}
