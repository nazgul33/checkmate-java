package com.skplanet.checkmate.querycache;

import com.skplanet.checkmate.utils.MailSender;
import org.apache.commons.lang.time.DateUtils;

import java.util.Date;

/**
 * Created by nazgul33 on 4/8/16.
 */
public class QCQueryMail extends MailSender.Mail {
  public QCQueryMail(QCQuery.QueryExport query, String subject, String content) {
    super(subject, content);
    setContent( content + "\n\n" + queryToMailContent(query) );
  }

  static String queryToMailContent(QCQuery.QueryExport query) {
    StringBuilder sb = new StringBuilder();
    /*
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
     */
    sb.append("Server : ").append(query.server).append('\n');
    sb.append("QueryId : ").append(query.id).append('\n');
    sb.append("Backend : ").append(query.backend).append('\n');
    sb.append("User : ").append(query.user).append('\n');
    sb.append("State : ").append(query.state).append('\n');
    sb.append("Fetched Rows : ").append(query.rowCnt).append('\n');
    sb.append("StartTime : ").append( new Date(query.startTime) ).append('\n');
    sb.append("Now : ").append( new Date() ).append('\n');

    sb.append("SQL : ").append(query.statement).append('\n');

    return sb.toString();
  }
}
