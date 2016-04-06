<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions"  prefix="fn"%>
<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean"/>
<html>
<head>
	<meta charset="utf-8" />
	<title>CheckMate :: QueryCache :: Queries</title>
</head>
<body>
<%@include file="header.jsp"%>
<link rel="stylesheet" type="text/css" href="/ladda/ladda-themeless.min.css">
<script src="/ladda/spin.min.js"></script>
<script src="/ladda/ladda.min.js"></script>
<%@include file="queries.funcs.html"%>
<script type="text/javascript">
	$(function() {
		$('#header-title').html('QueryCache :: Queries</span>');
		$('#headerMenuQueries').removeClass('btn-primary').addClass('btn-warning').attr('disabled', 'disabled');

		if (g_cluster_name == null || g_cluster_name.length == 0) {
			<c:if test="${fn:length(clusters.qcClusters) > 0}">
				window.location.href=window.location.href + "?cluster=${clusters.qcClusters[0]}";
			</c:if>
		}
		else {
			g_rq_table = $('#queriesinflight tbody');
			g_cq_table = $('#queriescomplete tbody');
			getRunningQueries();
			getCompleteQueries();
			initWebSocket();

			setInterval(pingWebSocket, 60*1000);
			//setInterval(getRunningQueries, 30*1000);
			setInterval(updateElapsedTimeRQ, 100);

			$('#refreshcq').text('Manual Refresh');
			$('#imgcqrefreshinprogress').hide();
		}
	});

</script>

<div class="container-fluid" align="center">
	<div class="cmscrollbar" style="width: 90%; height: 30%; text-align:left; position: relative;">
		<H2>In-Flight Queries</H2>
		<table class="table table-striped small" id="queriesinflight">
			<thead><tr>
				<th>server</th><th>id</th><th>type</th><th>user</th><th>statement</th><th>state</th><th>client ip</th><th>rows</th><th>startTime</th><th>elapsedTime</th><th>Cancel</th>
			</tr></thead>
			<tbody>
			</tbody>
		</table>
	</div>

	<div style="width: 90%; text-align:left; position: relative;">
		<H2>Complete Queries  <a class="btn btn-primary btn-xs" href="#" onclick="refreshCQ();" id="refreshcq">Manual Refresh</a> <img id="imgcqrefreshinprogress" src="/images/inprogress.gif" /></H2>
		<table class="table table-striped small" id="queriescomplete">
			<thead><tr>
				<th>server</th><th>id</th><th>type</th><th>user</th><th>statement</th><th>state</th><th>client ip</th><th>rows</th><th>startTime</th><th>elapsedTime</th>
			</tr></thead>
			<tbody>
			</tbody>
		</table>
	</div>
</div>
</body>
</html>

