package com.skplanet.checkmate.yarn;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by nazgul33 on 8/5/15.
 */
public class YarnMonitor {
	
	Timer updateTimer = new Timer(true);
	YarnResourceManager yarn = new YarnResourceManager();

	private static YarnMonitor instance;
	public static YarnMonitor getInstance() {
		if (instance == null) {
			instance = new YarnMonitor();
		}
		return instance;
	}

	private YarnMonitor() {
		updateTimer.scheduleAtFixedRate(
				new TimerTask() {
					public void run() {
						yarn.GetUnfinishedApplications();
					}
				},
				5000,
				5000
				);
	}
}
