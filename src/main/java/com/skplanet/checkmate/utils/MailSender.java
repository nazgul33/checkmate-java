package com.skplanet.checkmate.utils;

/**
 * Created by nazgul33 on 4/7/16.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * Created by nazgul33 on 10/26/15.
 */
public class MailSender implements Runnable {
  protected static final Logger LOG = LoggerFactory.getLogger(MailSender.class);

  static public class Mail {
    public final String subject;
    private String content;

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

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }
  }

  private final String smtpServer, smtpFrom;
  private final int smtpPort;
  private final InternetAddress[] recipientAddresses;

  public MailSender(String smtpServer, String smtpFrom, int smtpPort, List<String> recipients) throws Exception {
    this.smtpServer = smtpServer;
    this.smtpFrom = smtpFrom;
    this.smtpPort = smtpPort;

    recipientAddresses = new InternetAddress[recipients.size()];
    for (int i=0; i<recipients.size(); i++)
      recipientAddresses[i] = new InternetAddress(recipients.get(i));
  }

  private final List<Mail> mailQueue = new ArrayList<>();
  public void addMail(Mail mail) {
    synchronized (mailQueue) {
      mailQueue.add(mail);
    }
    LOG.info("mail queued. {}", mail.subject);
  }

  public void run() {
    try {
      Properties properties = System.getProperties();
      properties.setProperty("mail.smtp.host", smtpServer);
      properties.setProperty("mail.smtp.port", String.valueOf(smtpPort));

      Session session = Session.getDefaultInstance(properties);

      InternetAddress iFrom = new InternetAddress(smtpFrom);
      Collection<Mail> mq = new ArrayList<>();

      LOG.info("MailSender started.");

      while (!Thread.interrupted()) {
        synchronized (mailQueue) {
          if (mailQueue.size() > 0) {
            for (Mail mail : mailQueue) {
              mq.add(mail);
            }
            mailQueue.clear();
          }
        }
        if (mq.size() == 0) {
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            // ignore
          }
          continue;
        }

        for (Mail mail : mq) {
          LOG.info("sending mail '{}'", mail.subject);

          try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(iFrom);
            message.addRecipients(Message.RecipientType.TO, recipientAddresses);
            message.setSubject(mail.subject);
            message.setText(mail.content);

            Transport.send(message);
            LOG.info("Mail sent to {}", recipientAddresses);
          } catch (MessagingException e) {
            LOG.error("Exception sending mail", e);
          }
        }
        mq.clear();
      }
    } catch (Exception e) {
      LOG.error("Unexpected exception", e);
    }
  }

  public static void main(String args[]) {
    String server = null;
    int port = 25;
    String from = null;
    List<String> recipients = null;
    String title = null;
    String msg = null;
    try {
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "-s":
            server = args[++i];
            System.out.println("Server : " + server);
            break;
          case "-p":
            port = Integer.valueOf(args[++i]);
            System.out.println("Port   : " + port);
            break;
          case "-f":
            from = args[++i];
            System.out.println("From   : " + from);
            break;
          case "-r":
            recipients = Arrays.asList(args[++i].split(","));
            System.out.println("Recipients : " + recipients);
            break;
          case "-t":
            title = args[++i];
            System.out.println("Title  : " + title);
            break;
          case "-m":
            msg = args[++i];
            System.out.println("Message : " + msg);
            break;
          default:
            System.out.println("unknown option");
            break;
        }
      }
    } catch (Exception e) {
      System.err.println("error parsing arguments.");
      e.printStackTrace();
      System.exit(1);
    }

    MailSender mailSender = null;
    try {
      mailSender = new MailSender(server, from, port, recipients);
    } catch (Exception e) {
      System.err.println("error instantiating MailSender");
      e.printStackTrace();
      System.exit(1);
    }

    Thread t = new Thread(mailSender);
    t.setDaemon(false);
    t.start();

    Mail mail = new Mail(title, msg);
    mailSender.addMail(mail);

    try {
      Thread.sleep(10);
      t.interrupt();
      t.join();
    } catch (Exception e) {
      // ignore
    }
  }
}
