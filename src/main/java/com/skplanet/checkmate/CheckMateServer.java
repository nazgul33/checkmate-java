package com.skplanet.checkmate;

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
    private CheckMateWeb webServer;

    private static CheckMateServer _this = null;
    
    private CheckMateServer(File homeDir) throws Exception {

    	confDir = new File(homeDir, "conf");

    	File iniFile = new File(confDir,"checkmate.ini");
        LOG.info("reading checkmate configuration from " + iniFile);

        ini = new HierarchicalINIConfiguration(iniFile);
        
        SubnodeConfiguration globalSection = ini.getSection("global");
        globalSection.setThrowExceptionOnMissing(true);
        
        // MailSender
        {
            SubnodeConfiguration email = ini.getSection("email");
            email.setThrowExceptionOnMissing(true);
            
            String server = email.getString("SmtpServer", null);
            String from   = email.getString("SmtpFrom", "CheckMate<noreply@checkmate.com>");
            int port      = email.getInt("SmtpPort", 25);
            String tos    = email.getString("MailRecipients", null);

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
        	int port = globalSection.getInt("WebPort", 8080);
        	webServer = new CheckMateWeb(port, homeDir, metrics);
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
