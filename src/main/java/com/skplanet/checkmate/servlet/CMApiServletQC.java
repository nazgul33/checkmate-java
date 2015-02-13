package com.skplanet.checkmate.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.skplanet.checkmate.querycache.QCCluster;
import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.checkmate.querycache.QCQuery;
import com.skplanet.checkmate.utils.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;

/**
 * Created by nazgul33 on 15. 1. 29.
 */
public class CMApiServletQC extends HttpServlet {
    private static final boolean DEBUG = false;
    private static final String ASYNC_REQ_ATTR = CMApiServletQC.class.getName() + ".async";
    private static final String ASYNC_RETURN_ATTR = CMApiServletQC.class.getName() + ".async.return";
    private static final Logger LOG = LoggerFactory.getLogger("api");
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        response.setContentType("application/json; charset=utf-8");
        response.setContentType("application/json; charset=utf-8");

        if (DEBUG) {
            LOG.debug(request.getRequestURI() + "?" + request.getQueryString());
        }

        PrintWriter writer = response.getWriter();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        QCClusterManager qcMgr = QCClusterManager.getInstance();
        Gson gson = new Gson();
        switch (path) {
            case "/clusterList": {
                Collection<String> cl = qcMgr.getClusterList();
                Type qListType = new TypeToken<Collection<String>>() {}.getType();
                String jsonObj = gson.toJson(cl, qListType);
                writer.print(jsonObj);
                response.setStatus(HttpServletResponse.SC_OK);
                break;
            }
            case "/runningQueries": {
                String cluster = request.getParameter("cluster");
//                String server = request.getParameter("server");

                // if async marker is not set, check update time and initiate async
                if ( request.getAttribute(ASYNC_REQ_ATTR) == null ) {
                    long clientLastUpdate;
                    try {
                        clientLastUpdate = Long.valueOf(request.getParameter("lastUpdate"), 10);
                    } catch (NumberFormatException e) {
                        clientLastUpdate = 0;
                    }
                    long myLastUpdate = qcMgr.getLastUpdateTime(cluster);
                    if (myLastUpdate < 0) {
                        LOG.error(request.getRequestURI() + " cluster " + cluster + " not found");
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        break;
                    }

                    if (myLastUpdate <= clientLastUpdate) {
                        // wait for next update
                        final AsyncContext async = request.startAsync();
                        final Boolean asyncYes = new Boolean(true);
                        request.setAttribute(ASYNC_REQ_ATTR, asyncYes);
                        async.setTimeout(20000);
                        qcMgr.addAsyncContextRQ(async, cluster);
                        return;
                    }
                }

                Collection<QCQuery.QueryExport> ql = qcMgr.getExportedRunningQueries(cluster);
                Type qListType = new TypeToken<Collection<QCQuery.QueryExport>>() {}.getType();
                String jsonObj = gson.toJson(ql, qListType);
                writer.printf("{\"time\":%d, \"rq\":%s}", new Date().getTime(), jsonObj);
                response.setStatus(HttpServletResponse.SC_OK);
                break;
            }
            case "/completeQueries": {
                String cluster = request.getParameter("cluster");
//                String server = request.getParameter("server");
                Collection<QCQuery.QueryExport> ql = qcMgr.getExportedCompleteQueries(cluster);

                // TODO: paging, filtering
//                int page_size = 100;
//                int page_number = 0;
//                int lastIdx = (page_number + 1)*page_size;
//                if (lastIdx > ql.size() )
//                    lastIdx = ql.size();
//                if (page_number*page_size > ql.size()) {
//                    // page out of bound
//                    return
//                }
//                return ql.subList(page_number*page_size, (page_number + 1)*page_size);

                if (ql != null) {
                    Type qListType = new TypeToken<Collection<QCQuery.QueryExport>>() {
                    }.getType();
                    String jsonObj = gson.toJson(ql, qListType);
                    writer.print(jsonObj);
                    response.setStatus(HttpServletResponse.SC_OK);
                }
                else {
                    LOG.error(request.getRequestURI() + " cluster " + cluster + " not found");
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                break;
            }
            case "/sysInfoList": {
                String cluster = request.getParameter("cluster");
                Collection<QCCluster.SystemInfo> l = qcMgr.getSystemInfoList(cluster);
                if (l != null) {
                    Type lType = new TypeToken<Collection<QCCluster.SystemInfo>>() {
                    }.getType();
                    String jsonObj = gson.toJson(l, lType);
                    writer.print(jsonObj);
                    response.setStatus(HttpServletResponse.SC_OK);
                }
                else {
                    LOG.error(request.getRequestURI() + " cluster " + cluster + " not found");
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                break;
            }
            case "/poolInfoList": {
                String cluster = request.getParameter("cluster");
                Collection<QCCluster.PoolInfo> l = qcMgr.getPoolInfoList(cluster);
                if (l != null) {
                    Type lType = new TypeToken<Collection<QCCluster.PoolInfo>>() {
                    }.getType();
                    String jsonObj = gson.toJson(l, lType);
                    writer.print(jsonObj);
                    response.setStatus(HttpServletResponse.SC_OK);
                }
                else {
                    LOG.error(request.getRequestURI() + " cluster " + cluster + " not found");
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                break;
            }
            case "/cancelQuery": {
                // TODO : build async task which sends request to querycache.
                if ( request.getAttribute(ASYNC_REQ_ATTR) == null ) {
                    String cluster = request.getParameter("cluster");
                    final String cuqId = request.getParameter("cuqId");
                    final QCCluster c = qcMgr.getCluster(cluster);
                    if (cluster == null || cluster.length() == 0 || cuqId == null || cuqId.length() == 0) {
                        response.setContentType("application/json; charset=utf-8");
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        writer.printf("{\"result\":\"%s\", \"msg\":\"%s\"}", "error", "Invalid parameter.");
                        break;
                    }
                    final QCQuery.QueryExport eq = c.getExportedRunningQueryByCuqId(cuqId);
                    if ( eq == null ) {
                        response.setContentType("application/json; charset=utf-8");
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        writer.printf("{\"result\":\"%s\", \"msg\":\"%s\"}", "error", "query finished already.");
                        break;
                    }

                    LOG.info("Starting cancel async job. " + eq.server + " " + eq.backend + " " + eq.id);
                    final AsyncContext async = request.startAsync();
                    request.setAttribute(ASYNC_REQ_ATTR, new Boolean(true));
                    async.setTimeout(30000);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HttpUtil httpUtil = new HttpUtil();
                            StringBuffer buf = new StringBuffer();
                            int res = 0;
                            try {
                                String url = "http://" + eq.server + ":" + c.getOpt().webPort + "/api/cancelQuery?id="+eq.id+"&driver="+eq.backend;

                                httpUtil.get( url, buf );
                                async.getRequest().setAttribute(ASYNC_RETURN_ATTR, buf.toString());
                                if (DEBUG) {
                                    LOG.debug("Cancel reply " + buf.toString());
                                }
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
                        LOG.error("AsyncTask didn't set valid return message.");
                    }
                    response.setContentType("application/json; charset=utf-8");
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    writer.print(reply);
                }
            }
            default: {
                LOG.error(request.getRequestURI() + " not found..");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                break;
            }
        }
    }
}
