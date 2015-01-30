package com.skplanet.checkmate.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.skplanet.checkmate.querycache.QCCluster;
import com.skplanet.checkmate.querycache.QCClusterManager;
import com.skplanet.checkmate.querycache.QCQuery;
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
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Date;

/**
 * Created by nazgul33 on 15. 1. 29.
 */
public class CMApiServletQC extends HttpServlet {
    private static final String ASYNC_REQ_ATTR = CMApiServletQC.class.getName() + ".async";
    private static final Logger LOG = LoggerFactory.getLogger("api");
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        response.setContentType("application/json; charset=utf-8");
        response.setContentType("application/json; charset=utf-8");

        LOG.info(request.getRequestURI() + "?" + request.getQueryString());

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

                Collection<QCQuery.QueryExport> ql = qcMgr.exportRunningQueries(cluster);
                Type qListType = new TypeToken<Collection<QCQuery.QueryExport>>() {}.getType();
                String jsonObj = gson.toJson(ql, qListType);
                writer.printf("{\"time\":%d, \"rq\":%s}", new Date().getTime(), jsonObj);
                response.setStatus(HttpServletResponse.SC_OK);
                break;
            }
            case "/completeQueries": {
                String cluster = request.getParameter("cluster");
//                String server = request.getParameter("server");
                Collection<QCQuery.QueryExport> ql = qcMgr.exportCompleteQueries(cluster);
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
            default: {
                LOG.error(request.getRequestURI() + " not found..");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                break;
            }
        }
    }
}
