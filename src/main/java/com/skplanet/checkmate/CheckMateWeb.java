package com.skplanet.checkmate;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.MetricsServlet;
import com.skplanet.checkmate.servlet.CMApiServletQC;
import com.skplanet.checkmate.servlet.CMWebSocketServletQC;

public class CheckMateWeb {

	private static final Logger LOG = LoggerFactory.getLogger(CheckMateWeb.class);

	private Server webServer = null;

	public CheckMateWeb(int port, File rootDir, MetricRegistry metric) throws Exception {
		LOG.info("Starting web interface...");
		webServer = new Server(port);

		File webRoot = new File(rootDir,"www");
		if (!webRoot.exists()) {
			throw new FileNotFoundException("Unable to find resource " + webRoot);
		}
		// Points to wherever /www/ (the resource) is
		URI baseUri = webRoot.toURI();

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
		metrics.addServlet(new ServletHolder(new MetricsServlet(metric)), "/metrics");

		//        File scratchDir = getScratchDir();
		WebAppContext context = new WebAppContext();
		context.setContextPath("/");
		//        context.setAttribute("javax.servlet.context.tempdir", scratchDir);
		context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
				".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");
		context.setResourceBase(webRoot.toString());
		context.setAttribute("org.eclipse.jetty.containerInitializers", getJspInitializers());
		context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
		context.addBean(new ServletContainerInitializersStarter(context), true);
		context.setClassLoader(getUrlClassLoader());
		context.addServlet(getJspServletHolder(), "*.jsp");
		context.addServlet(getDefaultServletHolder(baseUri), "/");
		//        ServletContextHandler webappHandler = getWebAppContext(baseUri, scratchDir);

		ContextHandlerCollection contextCollection = new ContextHandlerCollection();
		contextCollection.setHandlers( new Handler[] { apiQc, metrics, context } );

		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[]{contextCollection});
		webServer.setHandler(handlers);
	}

	private List<ContainerInitializer> getJspInitializers() {
		JettyJasperInitializer sci = new JettyJasperInitializer();
		ContainerInitializer initializer = new ContainerInitializer(sci, null);
		List<ContainerInitializer> initializers = new ArrayList<>();
		initializers.add(initializer);
		return initializers;
	}

	private ClassLoader getUrlClassLoader() {
		ClassLoader jspClassLoader = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
		return jspClassLoader;
	}

	private ServletHolder getJspServletHolder() {
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

	private ServletHolder getDefaultServletHolder(URI baseUri) {
		ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);
		LOG.info("Base URI: " + baseUri);
		holderDefault.setInitParameter("resourceBase", baseUri.toASCIIString());
		holderDefault.setInitParameter("dirAllowed", "false");
		holderDefault.setInitParameter("redirectWelcome", "true");
		holderDefault.setInitParameter("welcomeServlets", "false");

		return holderDefault;
	}

	public void runWebServer() throws Exception {

		webServer.start();
		webServer.join();
	}
}
