package com.skplanet.checkmate;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.MetricsServlet;
import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.checkmate.servlet.CMApiServletQC;
import com.skplanet.checkmate.servlet.CMWebSocketServletQC;
import com.skplanet.checkmate.yarn.YarnMonitor;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */
public class CheckMateServer
{
    private static final Logger LOG = LoggerFactory.getLogger("main");
    private static final MetricRegistry metrics = new MetricRegistry();

    public static MetricRegistry getMetrics() {
        return metrics;
    }

    private void StartClusterManagers() {
        LOG.info("Starting QueryCache Cluster Thread");
        QCClusterManager.getInstance().startThread();

//        LOG.info("Starting Yarn Monitor");
//        YarnMonitor.getInstance();
    }
    private void ShutdownClusterManagers() {
        LOG.info("Shutting down QueryCache Cluster Thread");
        QCClusterManager.getInstance().finishThread();
    }

    private Server webServer = null;
    private int webServicePort;
    private String home = null;
    private String wwwroot = null;
    private static final String WEBROOT_INDEX = "/www/";

    private void readConfiguration() {
        home = System.getenv("CM_HOME");
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

    private URI getWebRootResourceUri() throws FileNotFoundException, URISyntaxException, MalformedURLException
    {
        // try local resource first
        wwwroot = home + "/www";
        URL indexUri = new File(wwwroot).toURI().toURL();
        if (indexUri == null)
        {
            // fallback to resources in jar
            wwwroot = WEBROOT_INDEX;
            indexUri = this.getClass().getResource(wwwroot);
            throw new FileNotFoundException("Unable to find resource " + wwwroot);
        }
        // Points to wherever /webroot/ (the resource) is
        return indexUri.toURI();
    }

    private File getScratchDir() throws IOException
    {
        File scratchDir = new File(home, "/tmp/jetty-jsp");

        if (!scratchDir.exists())
        {
            LOG.info("making scratch directory " + scratchDir.toString());
            if (!scratchDir.mkdirs())
            {
                LOG.error("making scratch directory failed.");
                throw new IOException("Unable to create scratch directory: " + scratchDir);
            }
        }
        return scratchDir;
    }

    private List<ContainerInitializer> jspInitializers()
    {
        JettyJasperInitializer sci = new JettyJasperInitializer();
        ContainerInitializer initializer = new ContainerInitializer(sci, null);
        List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(initializer);
        return initializers;
    }

    private ClassLoader getUrlClassLoader()
    {
        ClassLoader jspClassLoader = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
        return jspClassLoader;
    }

    private ServletHolder jspServletHolder()
    {
        ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
        holderJsp.setInitOrder(0);
        holderJsp.setInitParameter("logVerbosityLevel", "DEBUG");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.7");
        holderJsp.setInitParameter("compilerSourceVM", "1.7");
        holderJsp.setInitParameter("keepgenerated", "true");
        return holderJsp;
    }

    private ServletHolder defaultServletHolder(URI baseUri)
    {
        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);
        LOG.info("Base URI: " + baseUri);
        holderDefault.setInitParameter("resourceBase", baseUri.toASCIIString());
        holderDefault.setInitParameter("dirAllowed", "false");
        holderDefault.setInitParameter("redirectWelcome", "true");
        holderDefault.setInitParameter("welcomeServlets", "false");

        return holderDefault;
    }

    private WebAppContext getWebAppContext(URI baseUri, File scratchDir)
    {
        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        context.setAttribute("javax.servlet.context.tempdir", scratchDir);
        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
            ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");
        context.setResourceBase(baseUri.toASCIIString());
        context.setAttribute("org.eclipse.jetty.containerInitializers", jspInitializers());
        context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        context.addBean(new ServletContainerInitializersStarter(context), true);
        context.setClassLoader(getUrlClassLoader());

        context.addServlet(jspServletHolder(), "*.jsp");
        // Add Application Servlets
        // context.addServlet(BeanMapper.class, "*.cm");
        context.addServlet(defaultServletHolder(baseUri), "/");
        return context;
    }

    public Server runWebInterface() throws Exception {
        // create web service
        LOG.info("Starting web interface...");
        webServer = new Server(webServicePort);

        URI baseUri = getWebRootResourceUri();

        System.setProperty("org.apache.jasper.compiler.disablejsr199","false");

//        ServletContextHandler apiImpala = new ServletContextHandler(ServletContextHandler.SESSIONS);
//        apiImpala.setContextPath("/api/impala");
//        apiImpala.addServlet(new ServletHolder(new QCWebApiServlet()), "/*");

        ServletContextHandler apiQc = new ServletContextHandler(ServletContextHandler.SESSIONS);
        apiQc.setContextPath("/api/qc");
        apiQc.addServlet(new ServletHolder(new CMWebSocketServletQC()), "/websocket/*");
        apiQc.addServlet(new ServletHolder(new CMApiServletQC()), "/*");

        ServletContextHandler metrics = new ServletContextHandler(ServletContextHandler.SESSIONS);
        metrics.setContextPath("/api/metrics");
        metrics.addServlet(new ServletHolder(new MetricsServlet(getMetrics())), "/metrics");

        File scratchDir = getScratchDir();
        ServletContextHandler webappHandler = getWebAppContext(baseUri, scratchDir);

        ContextHandlerCollection contextCollection = new ContextHandlerCollection();
        contextCollection.setHandlers( new Handler[] { apiQc, metrics, webappHandler } );

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{contextCollection});
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
        server.StartClusterManagers();

        try {
            server.runWebInterface();
        } catch (Exception e) {
            LOG.error("exception while starting web interface");
            System.exit(1);
        }

        // run forever until interrupted.
        try {
            server.webServer.join();
        } catch (Exception e) {
            LOG.error("Checkmate Interrupted." + e.toString());
        }
        server.ShutdownClusterManagers();
    }
}
