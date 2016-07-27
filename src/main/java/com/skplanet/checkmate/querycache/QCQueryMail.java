package com.skplanet.checkmate.querycache;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.skplanet.checkmate.utils.MailSender;

/**
 * Created by nazgul33 on 4/8/16.
 */
public class QCQueryMail extends MailSender.Mail {
	
	public QCQueryMail(QCQuery query, String subject, String content) {
		super(subject);
		setContent(content + "\n\n" + queryToMailContent(query) );
	}

	public static String queryToMailContent(QCQuery query) {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
		sb.append("Server : ").append(query.getServer()).append('\n');
		sb.append("QueryId : ").append(query.getId()).append('\n');
		sb.append("Backend : ").append(query.getBackend()).append('\n');
		sb.append("User : ").append(query.getUser()).append('\n');
		sb.append("State : ").append(query.getState()).append('\n');
		sb.append("Fetched Rows : ").append(query.getRowCnt()).append('\n');
		sb.append("StartTime : ").append( sdf.format(new Date(query.getStartTime())) ).append('\n');
		sb.append("Now : ").append( sdf.format(new Date()) ).append('\n');

		sb.append("SQL : ").append(query.getStatement()).append('\n');

		return sb.toString();
	}
}
