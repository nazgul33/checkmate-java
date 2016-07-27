package com.skplanet.checkmate.servlet;

import static java.lang.String.format;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.skplanet.checkmate.CheckMateServer;
import com.skplanet.checkmate.querycache.QCCluster;
import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.checkmate.querycache.QCQuery;
import com.skplanet.checkmate.querycache.data.ClusterServerPoolInfo;
import com.skplanet.checkmate.querycache.data.ClusterServerSysStats;
import com.skplanet.checkmate.utils.HttpUtil;

/**
 * Created by nazgul33 on 15. 1. 29.
 */
public class CMApiServletQC extends HttpServlet {
	
	private static final long serialVersionUID = 2564648276343514634L;
	
    private static final Logger LOG = LoggerFactory.getLogger(CMApiServletQC.class);

    private static final String ASYNC_REQ_ATTR = CMApiServletQC.class.getName() + ".async";
    private static final String ASYNC_RETURN_ATTR = CMApiServletQC.class.getName() + ".async.return";
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json; charset=utf-8");

        LOG.info("{}?{}",request.getRequestURI(),request.getQueryString());

        PrintWriter writer = response.getWriter();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String urlWithParameter = request.getRequestURI();
        if (request.getQueryString() != null) {
            urlWithParameter += "?" + request.getQueryString();
        }

        QCClusterManager qcMgr = CheckMateServer.getClusterManager();
        Gson gson = new Gson();
        switch (path) {
            case "/clusterList": {
                List<String> cl = qcMgr.getClusterNameList();
                Type qListType = new TypeToken<List<String>>() {}.getType();
                String jsonObj = gson.toJson(cl, qListType);
                writer.printf("{\"result\":\"ok\", \"msg\":\"ok\", \"data\":%s}", jsonObj);
                response.setStatus(HttpServletResponse.SC_OK);
                break;
            }
            case "/runningQueries": {
                QCCluster c = qcMgr.getCluster(request.getParameter("cluster"));
                if (c!=null) {
                    List<QCQuery> ql = c.getRunningQueries();
                    Type qListType = new TypeToken<List<QCQuery>>() {
                    }.getType();
                    String jsonObj = gson.toJson(ql, qListType);
                    writer.printf("{\"result\":\"ok\", \"msg\":\"ok\", \"data\":%s}", jsonObj);
                    response.setStatus(HttpServletResponse.SC_OK);
                }
                else {
                    LOG.error(urlWithParameter + " : invalid parameter");
                    writer.print("{\"result\":\"error\", \"msg\":\"invalid parameter\"}");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                break;
            }

            case "/completeQueries": {
                QCCluster c = qcMgr.getCluster(request.getParameter("cluster"));
                if (c!=null) {
                    Collection<QCQuery> ql = c.getCompleteQueries();

                    /*
                    // TODO: paging, filtering
                    int page_size = 100;
                    int page_number = 0;
                    int lastIdx = (page_number + 1)*page_size;
                    if (lastIdx > ql.size() )
                        lastIdx = ql.size();
                    if (page_number*page_size > ql.size()) {
                        // page out of bound
                        return
                    }
                    return ql.subList(page_number*page_size, (page_number + 1)*page_size);
                    */

                    Type qListType = new TypeToken<Collection<QCQuery>>() {}.getType();
                    String jsonObj = gson.toJson(ql, qListType);
                    writer.printf("{\"result\":\"ok\", \"msg\":\"ok\", \"data\":%s}", jsonObj);
                    response.setStatus(HttpServletResponse.SC_OK);
                }
                else {
                    LOG.error(urlWithParameter + " : invalid parameter");
                    writer.print("{\"result\":\"error\", \"msg\":\"invalid parameter\"}");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                break;
            }
            case "/sysInfoList": {
                QCCluster c = qcMgr.getCluster(request.getParameter("cluster"));
                if (c!=null) {
                    List<ClusterServerSysStats> list = c.getServerInfo();
                    Type lType = new TypeToken<List<ClusterServerSysStats>>() {}.getType();
                    JsonArray jarr = gson.toJsonTree(list , lType).getAsJsonArray();
                    
                    JsonObject json = new JsonObject();
                    json.addProperty("result", "ok");
                    json.addProperty("msg",    "ok");
                    json.add("data",           jarr);
                    writer.print(json.toString());
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    LOG.error(urlWithParameter + " : invalid parameter");
                    JsonObject json = new JsonObject();
                    json.addProperty("result", "error");
                    json.addProperty("msg",    "invalid parameter");
                    writer.print(json.toString());
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                break;
            }
            case "/poolInfoList": {
                QCCluster c = qcMgr.getCluster(request.getParameter("cluster"));
                if (c != null) {
                    List<ClusterServerPoolInfo> list = c.getPoolInfo();
                    Type lType = new TypeToken<Collection<ClusterServerPoolInfo>>() {}.getType();
                    JsonArray jarr = gson.toJsonTree(list , lType).getAsJsonArray();
                    
                    JsonObject json = new JsonObject();
                    json.addProperty("result", "ok");
                    json.addProperty("msg",    "ok");
                    json.add("data",           jarr);
                    writer.print(json.toString());
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    LOG.error(urlWithParameter + " : invalid parameter");
                    JsonObject json = new JsonObject();
                    json.addProperty("result", "error");
                    json.addProperty("msg",    "invalid parameter");
                    writer.print(json.toString());
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                break;
            }
            case "/cancelQuery": {
                if ( request.getAttribute(ASYNC_REQ_ATTR) == null ) {
                    final String cuqId = request.getParameter("cuqId");
                    final QCCluster c = qcMgr.getCluster(request.getParameter("cluster"));
                    if (c == null || cuqId == null || cuqId.length() == 0) {
                        LOG.error(urlWithParameter + " : invalid parameter");
                        writer.print("{\"result\":\"error\", \"msg\":\"invalid parameter\"}");
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        break;
                    }
                    final QCQuery eq = c.getRunningQueryByCuqId(cuqId);
                    if ( eq == null ) {
                        response.setContentType("application/json; charset=utf-8");
                        response.setStatus(HttpServletResponse.SC_OK);
                        writer.printf("{\"result\":\"%s\", \"msg\":\"%s\"}", "error", "query finished already.");
                        LOG.error("cancelQuery invalid. cuqId " + cuqId + " not found");
                        break;
                    }

                    LOG.info("Starting cancel async job. " + eq.getServer() + " " + eq.getBackend() + " " + eq.getId());
                    final AsyncContext async = request.startAsync();
                    request.setAttribute(ASYNC_REQ_ATTR, new Boolean(true));
                    async.setTimeout(30000);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HttpUtil httpUtil = new HttpUtil();
                            StringBuilder buf = new StringBuilder();
                            try {
                                String url = format("http://%s:%s%s",
                                		eq.getServer(),
                                		c.getOptions().getWebPort(),
                                		"/api/cancelQuery?id="+eq.getId()+"&driver="+eq.getBackend());

                                httpUtil.get( url, buf );
                                async.getRequest().setAttribute(ASYNC_RETURN_ATTR, buf.toString());
                                LOG.info("Cancel reply " + buf.toString());
                            } catch (Exception e) {
                                async.getRequest().setAttribute(ASYNC_RETURN_ATTR,
                                        "{\"result\":\"error\", \"msg\":\"Cancel failed.\"}");
                                LOG.error("Cancel failed" + e);
                            }
                            async.dispatch();
                        }
                    }).start();
                } else {
                    String reply = (String) request.getAttribute(ASYNC_RETURN_ATTR);
                    if (reply == null || reply.length() == 0) {
                        LOG.error("Cancel AsyncTask didn't set valid return message.");
                        reply="{\"result\":\"error\", \"msg\":\"Result was not set.\"}";
                    }
                    response.setStatus(HttpServletResponse.SC_OK);
                    writer.print(reply);
                }
                break;
            }
            default: {
                LOG.error(request.getRequestURI() + " not found..");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                break;
            }
        }
    }
}
