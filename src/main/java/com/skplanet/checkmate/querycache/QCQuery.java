package com.skplanet.checkmate.querycache;

import java.util.Date;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class QCQuery {
    private static final boolean DEBUG = true;
    public static class QueryImport {
        public String queryId;
        public String connType;
        public String user;
        public String queryType;
        public String queryStr;
        public String clientIp;
        public String stmtState;
        public long rowCnt;
        public long startTime;
        public long endTime;
        // 0:exec/1:getmeta/2:fetch/3:stmtclose or
        // 1:getschemas/2:fetch
        public long[] timeHistogram = {0,0,0,0};
        public long[] execProfile   = null;
        public long[] fetchProfile  = {0,0,0,-1,-1,-1,-1,0,0,-1,-1};
    }

    public static class QueryExport {
        public String server;
        public String id;
        public String backend;
        public String user;
        public String type;
        public String statement;
        public String client;
        public String state;
        public long rowCnt = -1;
        public long startTime;
        public long endTime = 0;
        // 0:exec/1:getmeta/2:fetch/3:stmtclose or
        // 1:getschemas/2:fetch
        public long[] timeHistogram;
        public long[] execProfile;
        public long[] fetchProfile;
    }

    private QCServer server;

    public final String id;
    public final String backend;
    public final String user;
    public final String type;
    public final String statement;
    public final String client;
    public String state;
    public long rowCnt = -1;
    public final long startTime;
    public long endTime = 0;
    // 0:exec/1:getmeta/2:fetch/3:stmtclose or
    // 1:getschemas/2:fetch
    public long[] timeHistogram;
    public long[] execProfile;
    public long[] fetchProfile;

    public long updateTime = 0;

    public QCQuery(QCServer server, QueryImport qObj) {
        this.server = server;
        this.id = qObj.queryId;
        this.backend = qObj.connType;
        this.user = qObj.user;
        this.type = qObj.queryType;
        this.statement = qObj.queryStr;
        this.client = qObj.clientIp;
        this.state = qObj.stmtState;
        this.rowCnt = qObj.rowCnt;
        this.startTime = qObj.startTime;
        this.endTime = qObj.endTime;
        this.timeHistogram = qObj.timeHistogram;
        this.execProfile = qObj.execProfile;
        this.fetchProfile = qObj.fetchProfile;

        this.updateTime = new Date().getTime();
    }

    public void Update(QueryImport qObj) {
        if ( this.state != qObj.stmtState ) {
            if (DEBUG) System.console().printf("query %s update state %s -> %s\n", id, state, qObj.stmtState);
            this.state = qObj.stmtState;
        }
        if ( this.rowCnt != qObj.rowCnt ) {
            if (DEBUG) System.console().printf("query %s update rowCnt %s -> %s\n", id, rowCnt, qObj.rowCnt);
            this.rowCnt = qObj.rowCnt;
        }
        this.timeHistogram = qObj.timeHistogram;
        this.execProfile = qObj.execProfile;
        this.fetchProfile = qObj.fetchProfile;

        this.updateTime = new Date().getTime();
    }

    public QueryExport export() {
        QueryExport eq = new QueryExport();
        eq.server = server.name;
        eq.id = id;
        eq.backend = backend;
        eq.user = user;
        eq.type = type;
        eq.statement = statement;
        eq.client = client;
        eq.state = state;
        eq.rowCnt = rowCnt;
        eq.startTime = startTime;
        eq.endTime = endTime;
        eq.timeHistogram = new long[timeHistogram.length];
        System.arraycopy(eq.timeHistogram, 0, timeHistogram, 0, timeHistogram.length);
        if (execProfile != null) {
            eq.execProfile = new long[execProfile.length];
            System.arraycopy(eq.execProfile, 0, execProfile, 0, execProfile.length);
        }
        eq.fetchProfile = new long[fetchProfile.length];
        System.arraycopy(eq.fetchProfile, 0, fetchProfile, 0, fetchProfile.length);
        return eq;
    }
}
