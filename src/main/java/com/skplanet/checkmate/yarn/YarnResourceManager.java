package com.skplanet.checkmate.yarn;

import com.google.gson.Gson;
import com.skplanet.checkmate.utils.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by nazgul33 on 8/5/15.
 */
public class YarnResourceManager {
  private static final Logger LOG = LoggerFactory.getLogger("yarn");
  private long lastUpdated = 0;

  private Gson gson = new Gson();
  static class AppsContainer {
    static class AppContainer {
      List<YarnApplication> app;
    }
    AppContainer apps;
  }

  private List<YarnApplication> yarnApps = new ArrayList<YarnApplication>();

  void GetUnfinishedApplications() {
    final String url = "http://dicc-m002:10100/ws/v1/cluster/apps?states=NEW,RUNNING,";
    HttpUtil httpUtil = new HttpUtil();
    int responseCode = 0;
    try {
      StringBuffer buf = new StringBuffer(2048);
      responseCode = httpUtil.getJson(url, buf);
      LOG.info("http response {}", responseCode);
      if (responseCode == 200) {
        AppsContainer ac = gson.fromJson(buf.toString(), AppsContainer.class);
        yarnApps.clear();
        if (ac != null && ac.apps != null && ac.apps.app != null) {
          yarnApps.addAll(ac.apps.app);
        }
      }
    } catch (Exception e) {
      LOG.error("Getting active application list from resource manager", e);
    }

    for (YarnApplication a: yarnApps) {
      LOG.info("app {} progress {}", a.id, a.progress);
    }
  }
}
