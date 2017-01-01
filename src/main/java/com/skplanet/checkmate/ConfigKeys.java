package com.skplanet.checkmate;

public class ConfigKeys {

	public static final String  CHECKMATE_INI_FILE = "checkmate.ini";
	
	public static final String  CHECKMATE_GLOBAL_SECTION = "global";
	public static final String  CHECKMATE_WEB_PORT = "WebPort";
	public static final int     CHECKMATE_WEB_PORT_DEFAULT = 8082;
	public static final String  CHECKMATE_USER_WEBSOCET = "UseWebSocket";
	public static final boolean CHECKMATE_USER_WEBSOCET_DEFAULT = false;
	public static final String  QUERYCACHE_INI_FILE = "querycache.ini";
	
	public static final String  CHECKMATE_EMAIL_SECTION = "email";
	public static final String  CHECKMATE_EMAIL_SMTP_SERVER = "SmtpServer";
	public static final String  CHECKMATE_EMAIL_SMTP_FROM = "SmtpFrom";
	public static final String  CHECKMATE_EMAIL_SMTP_PORT = "SmtpPort";
	public static final int     CHECKMATE_EMAIL_STMP_PORT_DEFAULT = 25;
	public static final String  CHECKMATE_EMAIL_RECIPIENTS = "MailRecipients";
	
	// Global Section
	public static final String  PARTIAL_UPDATE_INTERAVAL = "PartialUpdateInterval";
	public static final long    PARTIAL_UPDATE_INTERVAL_DEFAULT = 5*1000;
	public static final String  FULL_UPDATE_INTERVAL = "FullUpdateInterval";
	public static final long    FULL_UPDATE_INTERVAL_DEFAULT = 10*1000; 
	public static final String  UPDATE_FAIL_SLEEP_TIME = "UpdateFailSleepTime";
	public static final int     UPDATE_FAIL_SLEEP_TIME_DEFAULT = 5*60*1000;
	
	// Cluster Section
	public static final String  CLUSTER_NAMES = "Clusters";
	public static final String  CLUSTER_WEB_PORT = "WebPort";
	public static final int     CLUSTER_WEB_PORT_DEFFAULT = 8081;
	public static final String  CLUSTER_SERVERS = "Servers";
	public static final String  CLUSTER_MAX_COMPLETE_QUERIES = "MaxComleteQueries";
	public static final int     CLUSTER_MAX_COMPLETE_QUERIES_DEFAULT = 100;
	public static final String  CLUSTER_USE_WEBSOCKET = "UseWebSocket";
	public static final boolean CLUSTER_USER_WEBSOCKE_DEFAULT = false;
	
	// Server Config
	public static final int     EVENT_QUEUE_MAX_LIMIT = 3000;
}
