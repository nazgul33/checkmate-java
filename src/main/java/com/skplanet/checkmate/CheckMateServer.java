package com.skplanet.checkmate;

import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.checkmate.servlet.CMApiServletQC;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hello world!
 *
 */
public class CheckMateServer
{
    private static final Logger LOG = LoggerFactory.getLogger("main");

    private void StartClusterManagers() {
        LOG.info("Starting QueryCache Cluster Thread");
        QCClusterManager.getInstance().startThread();
    }
    private void ShutdownClusterManagers() {
        LOG.info("Shutting down QueryCache Cluster Thread");
        QCClusterManager.getInstance().finishThread();
    }

    private Server webServer = null;
    private int webServicePort;

    private void readConfiguration() {
        String home = System.getenv("QC_HOME");
        if (home == null) {
            home = "./";
        }
        String conf = home + "/conf/checkmate.ini";
        HierarchicalINIConfiguration clusterConfigFile = null;
        LOG.info("reading checkmate configuration from " + conf);

        try {
            clusterConfigFile = new HierarchicalINIConfiguration(conf);
            SubnodeConfiguration globalSection = clusterConfigFile.getSection(null);
            globalSection.setThrowExceptionOnMissing(true);
            webServicePort = globalSection.getInt("WebServicePort", 8080);

        }
        catch (Exception e) {
            LOG.error("error loading configuration.", e);
            System.exit(1);
        }
    }

    public Server runWebInterface() throws Exception {
        // create web service
        LOG.info("Starting web interface...");
        webServer = new Server(webServicePort);

//        ServletContextHandler apiImpala = new ServletContextHandler(ServletContextHandler.SESSIONS);
//        apiImpala.setContextPath("/api/impala");
//        apiImpala.addServlet(new ServletHolder(new QCWebApiServlet()), "/*");

        ServletContextHandler apiQc = new ServletContextHandler(ServletContextHandler.SESSIONS);
        apiQc.setContextPath("/api/qc");
        apiQc.addServlet(new ServletHolder(new CMApiServletQC()), "/*");

        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(true);
        resource_handler.setWelcomeFiles(new String[]{ "index.html" });
        resource_handler.setResourceBase("./www/");

        ContextHandlerCollection contextCollection = new ContextHandlerCollection();
        contextCollection.setHandlers( new Handler[] { apiQc } );

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] {contextCollection, resource_handler});
        webServer.setHandler(handlers);
        webServer.start();
        LOG.info("Started web interface...");

        return webServer;
    }

    public CheckMateServer() {
        readConfiguration();
    }

    public static void main( String[] args )
    {
        final CheckMateServer server = new CheckMateServer();
        final AtomicBoolean quit = new AtomicBoolean(false);

        server.StartClusterManagers();

        try {
            server.runWebInterface();
        } catch (Exception e) {
            LOG.error("exception while starting web interface");
            System.exit(1);
        }

        SignalHandler sh = new SignalHandler() {
            public void handle(Signal sig) {
                quit.set(true);
            }
        };

        try {
            Signal.handle(new Signal("INT"), sh);
            Signal.handle(new Signal("TERM"), sh);
            Signal.handle(new Signal("HUP"), sh);
        }
        catch (IllegalArgumentException e) {

        }

        while (quit.get() == false) {
            try {
                Thread.sleep(500);
            }
            catch (InterruptedException e) {
            }
        }

        try {
            LOG.info("Shutting down querycache.");
            server.webServer.stop();
            server.webServer.join();
        } catch (Exception e) {
            LOG.error("exception while waiting for webServer shutdown." + e.toString());
        }
        server.ShutdownClusterManagers();
    }
}
