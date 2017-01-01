package com.skplanet.checkmate.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Created by nazgul33 on 4/7/16.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by nazgul33 on 10/26/15.
 */
public class MailSender {

	private static final Logger LOG = LoggerFactory.getLogger(MailSender.class);

	private static MailSender _this;
	
	private List<Mail> mailQueue = new ArrayList<>();
	
	private String server;
	private String from;
	private int port;
	private InternetAddress[] tos;
	private MailSenderThread thread;

	private MailSender(String server, int port, String from, String[] toList) throws Exception {
		this.server = server;
		this.port = port;
		this.from = from;

		tos = new InternetAddress[toList.length];
		for (int i=0;i<toList.length;i++) {
			tos[i] = new InternetAddress(toList[i]);
		}
		
		thread = new MailSenderThread();
	}

	public static void initialize(String server, int port, String from, String[] toList) throws Exception {
		if (_this == null) {
			_this = new MailSender(server, port, from, toList);
		}
	}
	
	public static void close() {
		if (_this != null && _this.thread.isAlive()) {
			_this.thread.close();
		}
	}
	
	public static void send(Mail mail) {
		if (_this != null) {
			_this.addMail(mail);
		}
	}
	
	private void addMail(Mail mail) {
		synchronized (mailQueue) {
			mailQueue.add(mail);
		}
		LOG.info("mail queued. {}", mail.subject);
	}

	private class MailSenderThread extends Thread {
		
		private boolean running = false;
		
		public MailSenderThread() {
			super("mail sender");
		}

		@Override
		public void run() {
			
			LOG.info("MailSender thread started.");
			
			running = true;
			try {
				Properties properties = System.getProperties();
				properties.setProperty("mail.smtp.host", server);
				properties.setProperty("mail.smtp.port", Integer.toString(port));
	
				Session session = Session.getDefaultInstance(properties);
	
				InternetAddress iFrom = new InternetAddress(from);
				List<Mail> mq = new ArrayList<>();
	
				while (running) {
					synchronized (mailQueue) {
						if (mailQueue.size() > 0) {
							mq.addAll(mailQueue);
							mailQueue.clear();
						}
					}
					for (Mail mail : mq) {
						LOG.info("sending mail '{}'", mail.getSubject());
	
						try {
							MimeMessage message = new MimeMessage(session);
							message.setFrom(iFrom);
							message.addRecipients(Message.RecipientType.TO, tos);
							message.setSubject(mail.getSubject());
							message.setText(mail.getContent());
	
							Transport.send(message);
							LOG.info("Mail sent to {}", Arrays.toString(tos));
						} catch (Exception e) {
							LOG.error("Exception sending mail", e);
						}
					}
					mq.clear();
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						break;
					}
				}
			} catch (Exception e) {
				LOG.error("Unexpected exception", e);
			}
			
			LOG.info("MailSender thread started.");
		}
		
		public void close() {
			running = false;
			interrupt();
		}
	}
	
	public static class Mail {
		private String subject;
		private String content;

		public Mail(String subject) {
			this(subject, (String)null);
		}
		
		public Mail(String subject, String content) {
			this.subject = subject;
			this.content = content;
		}

		public Mail(String subject, Exception e) {
			this.subject = subject;

			StringWriter errors = new StringWriter();
			errors.append("Exception : ").append(e.getClass().getCanonicalName()).append("\n");
			errors.append(e.getMessage()).append("\n");
			errors.flush();
			e.printStackTrace(new PrintWriter(errors));
			this.content = errors.toString();
		}

		public String getSubject() {
			return subject;
		}
		
		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}
	}
}
