package com.skplanet.checkmate.servlet;

import static java.lang.String.format;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.querycache.QCCluster;
import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.checkmate.querycache.data.ClusterServerPoolInfo;
import com.skplanet.checkmate.querycache.data.ClusterServerSysStats;
import com.skplanet.checkmate.utils.HttpUtil;
import com.skplanet.querycache.server.cli.QueryProfile;

/**
 * Created by nazgul33 on 15. 1. 29.
 */
public class CMQCApiServlet extends HttpServlet {

	private static final long serialVersionUID = 2564648276343514634L;

	private static final Logger LOG = LoggerFactory.getLogger(CMQCApiServlet.class);

	private static final String ASYNC_REQ_ATTR = CMQCApiServlet.class.getName() + ".async";
	private static final String ASYNC_RETURN_ATTR = CMQCApiServlet.class.getName() + ".async.return";

	private Gson gson = new Gson();
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("application/json; charset=utf-8");
		PrintWriter out = resp.getWriter();

		
		String path = req.getRequestURI().substring(req.getContextPath().length());
		String urlWithParameter = req.getRequestURI();
		if (req.getQueryString() != null) {
			urlWithParameter += "?" + req.getQueryString();
		}

		String clusterName = req.getParameter("cluster");
		QCClusterManager qcMgr = CheckMateServer.getClusterManager();
		QCCluster cluster = clusterName!=null?qcMgr.getCluster(clusterName):null;
		
		JsonObject json = new JsonObject();
		switch (path) {
		case "/clusterList": {
			List<String> cnameList = qcMgr.getClusterNameList();
			json.addProperty("result", "ok");
			json.addProperty("msg", "ok");
			json.add("data", gson.toJsonTree(cnameList, new TypeToken<List<String>>(){}.getType()));
			out.print(json.toString());
			resp.setStatus(HttpServletResponse.SC_OK);
			break;
		}
		case "/runningQueries": {
			if (cluster!=null) {
				List<QueryProfile> profileList = cluster.getRunningQueries();
				
				json.addProperty("result", "ok");
				json.addProperty("msg", "ok");
				json.add("data", gson.toJsonTree(profileList, new TypeToken<List<QueryProfile>>(){}.getType()).getAsJsonArray());
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_OK);
			} else {
				json.addProperty("result", "error");
				json.addProperty("msg", "invalid parameter");
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			break;
		}
		case "/completeQueries": {
			if (cluster!=null) {
				List<QueryProfile> profileList = cluster.getCompleteQueries();
				json.addProperty("result", "ok");
				json.addProperty("msg", "ok");
				json.add("data", gson.toJsonTree(profileList, new TypeToken<List<QueryProfile>>(){}.getType()).getAsJsonArray());
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_OK);
			} else {
				json.addProperty("result", "error");
				json.addProperty("msg", "invalid parameter");
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			break;
		}
		case "/sysInfoList": {
			if (cluster!=null) {
				List<ClusterServerSysStats> list = cluster.getServerInfo();
				json.addProperty("result", "ok");
				json.addProperty("msg", "ok");
				json.add("data", gson.toJsonTree(list, new TypeToken<List<ClusterServerSysStats>>(){}.getType()).getAsJsonArray());
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_OK);
			} else {
				json.addProperty("result", "error");
				json.addProperty("msg", "invalid parameter");
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			break;
		}
		case "/poolInfoList": {
			if (cluster != null) {
				List<ClusterServerPoolInfo> list = cluster.getPoolInfo();
				json.addProperty("result", "ok");
				json.addProperty("msg", "ok");
				json.add("data", gson.toJsonTree(list , new TypeToken<Collection<ClusterServerPoolInfo>>(){}.getType()).getAsJsonArray());
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_OK);
			} else {
				json.addProperty("result", "error");
				json.addProperty("msg", "invalid parameter");
				out.print(json.toString());
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			break;
		}
		case "/cancelQuery": {
			if (req.getAttribute(ASYNC_REQ_ATTR) == null ) {
				String cuqId = req.getParameter("cuqId");
				if (cluster != null && cuqId != null && cuqId.trim().length() > 0) {
					QueryProfile profile = cluster.getRunningQuery(cuqId);
					if (profile != null) {
						String server = profile.getServer();
						int port = cluster.getOptions().getWebPort();
						String queryId = profile.getQueryId();
						final AsyncContext async = req.startAsync();
						final String url = format("http://%s:%s/api/cancelQuery?queryId=%s", server, port, queryId);
						LOG.info("Starting cancel async job. {}:{}" + profile.getServer() + profile.getQueryId());
						req.setAttribute(ASYNC_REQ_ATTR, new Boolean(true));
						async.setTimeout(30000);
						new Thread(){
							@Override
							public void run() {
								HttpUtil httpUtil = new HttpUtil();
								StringBuilder buf = new StringBuilder();
								try {
									httpUtil.get( url, buf );
									async.getRequest().setAttribute(ASYNC_RETURN_ATTR, buf.toString());
								} catch (Exception e) {
									JsonObject json = new JsonObject();
									json.addProperty("result", "error");
									json.addProperty("msg", "Cancel failed. +"+e.getMessage());
									async.getRequest().setAttribute(ASYNC_RETURN_ATTR, json.toString());
								}
								async.dispatch();
							}
						}.start();
					} else {
						json.addProperty("result", "error");
						json.addProperty("msg", "query finished already");
						out.print(json.toString());
						resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					}
				} else {
					json.addProperty("result", "error");
					json.addProperty("msg", "invalid parameter");
					out.print(json.toString());
					resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				}
			} else {
				String reply = (String) req.getAttribute(ASYNC_RETURN_ATTR);
				if (reply == null || reply.length() == 0) {
					LOG.error("Cancel AsyncTask didn't set valid return message.");
					json.addProperty("result", "error");
					json.addProperty("msg", "Result was not set.");
					reply = json.toString();
				}
				out.print(reply);
				resp.setStatus(HttpServletResponse.SC_OK);
			}
			break;
		}
		default: {
			LOG.error("invalid url request. {}", urlWithParameter);
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			break;
		}
		}
	}
}
