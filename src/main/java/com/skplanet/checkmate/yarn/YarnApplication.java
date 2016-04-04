package com.skplanet.checkmate.yarn;

/**
 * Created by nazgul33 on 8/5/15.
 */
public class YarnApplication {
  public String id;
  public String user;
  public String name;
  public String queue;
  public String state;
  public String finalStatus;
  public double progress;
  public String trackingUI;
  public String trackingUrl;
  public String diagnostics;
  public long clusterId;
  public String applicationType;
  public String applicationTags;
  public long startedTime;
  public long finishedTime;
  public long elapsedTime;
  public String amContainerLogs;
  public String amHostHttpAddress;
  public int allocatedMB;
  public int allocatedVCores;
  public int runningContainers;
  public long memorySeconds;
  public long vcoreSeconds;
  public int preemptedResourceMB;
  public int preemptedResourceVCores;
  public int numNonAMContainerPreempted;
  public int numAMContainerPreempted;
}
