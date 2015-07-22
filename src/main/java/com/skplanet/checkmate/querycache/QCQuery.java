package com.skplanet.checkmate.querycache;

import org.apache.commons.codec.binary.Base64;

import java.util.Arrays;
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
        public String cuqId; // cluster unique query id
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
        public String cancelUrl;
    }

    private QCServer server;

    public final String cuqId;
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

        this.cuqId = getCuqId();
    }

    public boolean Update(QCQuery qObj) {
        boolean updated = false;
        if ( !this.state.equals(qObj.state) ) {
            if (DEBUG) System.console().printf("query %s update state %s -> %s\n", id, state, qObj.state);
            this.state = qObj.state;
            updated = true;
        }
        if ( this.rowCnt != qObj.rowCnt ) {
            if (DEBUG) System.console().printf("query %s update rowCnt %s -> %s\n", id, rowCnt, qObj.rowCnt);
            this.rowCnt = qObj.rowCnt;
            updated = true;
        }
        if ( !Arrays.equals(this.timeHistogram, qObj.timeHistogram) ) {
            this.timeHistogram = qObj.timeHistogram;
            updated = true;
        }
        if ( !Arrays.equals(this.execProfile, qObj.execProfile) ) {
            this.execProfile = qObj.execProfile;
            updated = true;
        }
        if ( !Arrays.equals(this.fetchProfile, qObj.fetchProfile) ) {
            this.fetchProfile = qObj.fetchProfile;
            updated = true;
        }

        if (updated) {
            this.updateTime = new Date().getTime();
        }
        return updated;
    }

    public String getCuqId() {
        String cuqid = new String(Base64.encodeBase64((server.name+backend+id).trim().toUpperCase().getBytes()));
        return cuqid.replace('+',':').replace('/','.').replace('=','-');
    }

    public QueryExport export() {
        QueryExport eq = new QueryExport();
        eq.cuqId = cuqId;
        // remove characters not supported by html4 standard for id field.

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
        // eq.cancelUrl = "http://" + server.name + ":" + server.cluster.getOpt().webPort + "/api/cancelQuery?id="+id+"&driver="+backend;
        eq.cancelUrl = "/api/qc/cancelQuery?cluster="+server.cluster.name+"&cuqId="+eq.cuqId;
        return eq;
    }
}
