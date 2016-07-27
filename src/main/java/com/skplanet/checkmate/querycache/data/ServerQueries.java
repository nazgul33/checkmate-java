package com.skplanet.checkmate.querycache.data;

import java.util.ArrayList;
import java.util.List;

public class ServerQueries {
	private List<QCQueryImport> runningQueries = new ArrayList<>();
	private List<QCQueryImport> completeQueries = new ArrayList<>();

	public List<QCQueryImport> getRunningQueries() {
		return runningQueries;
	}
	public void setRunningQueries(List<QCQueryImport> runningQueries) {
		this.runningQueries = runningQueries;
	}
	public List<QCQueryImport> getCompleteQueries() {
		return completeQueries;
	}
	public void setCompleteQueries(List<QCQueryImport> completeQueries) {
		this.completeQueries = completeQueries;
	}
}