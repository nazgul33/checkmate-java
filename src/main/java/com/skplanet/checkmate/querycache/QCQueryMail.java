package com.skplanet.checkmate.querycache;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.skplanet.checkmate.utils.MailSender;
import com.skplanet.querycache.server.cli.QueryProfile;

/**
 * Created by nazgul33 on 4/8/16.
 */
public class QCQueryMail extends MailSender.Mail {
	
	public QCQueryMail(QueryProfile query, String subject, String content) {
		super(subject);
		setContent(content + "\n\n" + queryToMailContent(query) );
	}

	public static String queryToMailContent(QueryProfile query) {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		StringBuilder sb = new StringBuilder();
		sb.append("Server : ").append(query.getServer()).append('\n');
		sb.append("QueryId : ").append(query.getQueryId()).append('\n');
		sb.append("User : ").append(query.getUserId()).append('\n');
		sb.append("State : ").append(query.getState()).append('\n');
		sb.append("Fetched Rows : ").append(query.getRowCnt()).append('\n');
		sb.append("StartTime : ").append( sdf.format(new Date(query.getStartTime())) ).append('\n');
		sb.append("Now : ").append( sdf.format(new Date()) ).append('\n');
		sb.append("SQL : ").append(query.getQuery()).append('\n');

		return sb.toString();
	}
}
