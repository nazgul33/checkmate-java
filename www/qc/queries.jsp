<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean" />
<%
boolean useWebSocket = clusters.getQcOption(request.getParameter("cluster")).isUseWebSocket();
%>
<html>
<head>
<meta charset="utf-8" />
<title>CheckMate :: QueryCache :: Queries</title>
</head>
<body>
	<%@include file="header.jsp"%>
	<%@include file="queries.funcs.html"%>
	<script type="text/javascript">
	$(function() {
		$('#header-title').html('QueryCache :: Queries</span>');
		$('#headerMenuQueries').removeClass('btn-primary').addClass('btn-warning').attr('disabled', 'disabled');

		if (cluster_name == null || cluster_name.length == 0) {
			<c:if test="${fn:length(clusters.qcClusters) > 0}">
				window.location.href=window.location.href + "?cluster=${clusters.qcClusters[0]}";
			</c:if>
		}
		else {
			rq_table = $('#queriesinflight tbody');
			cq_table = $('#queriescomplete tbody');
			getRunningQueries();
			getCompleteQueries();

			<c:if test="<%= useWebSocket %>">
			initWebSocket();
			setInterval(pingWebSocket, 60*1000);
			</c:if>
			<c:if test="<%= !useWebSocket %>">
			setInterval(getRunningQueries,60*1000);
			setInterval(getCompleteQueries,60*1000);
			</c:if>
			//setInterval(getRunningQueries, 30*1000);
			setInterval(updateElapsedTimeRQ, 1000);

			$('#refreshcq').text('Manual Refresh');
			$('#imgcqrefreshinprogress').hide();
		}
	});

</script>

	<div class="container-fluid" align="left" width="100%">
		<H2>In-Flight Queries</H2>
		<table class="table table-striped small fixed" id="queriesinflight">
			<thead>
				<tr>
					<th class="qServer">server</th>
					<th class="qId">id</th>
					<th class="qUser">user</th>
					<th class="qQuery">query</th>
					<th class="qState">state</th>
					<th class="qClient">client</th>
					<th class="qRows">rows</th>
					<th class="qStartTime">start</th>
					<th class="qElapsedTime">elapsed</th>
					<th class="qCancel">Cancel</th>
				</tr>
			</thead>
			<tbody>
			</tbody>
		</table>

		<H2>
			Complete Queries 
			<a class="btn btn-primary btn-xs" href="#" onclick="refreshCQ();" id="refreshcq">Manual Refresh</a> 
			<img id="imgcqrefreshinprogress" src="/images/inprogress.gif" />
		</H2>
		<table class="table table-striped small fixed" id="queriescomplete">
			<thead>
				<tr>
					<th class="qServer">server</th>
					<th class="qId">id</th>
					<th class="qUser">user</th>
					<th class="qQuery">query</th>
					<th class="qState">state</th>
					<th class="qClient">client</th>
					<th class="qRows">rows</th>
					<th class="qStartTime">start</th>
					<th class="qEndTime">end</th>
					<th class="qElapsedTime">elapsed</th>
				</tr>
			</thead>
			<tbody>
			</tbody>
		</table>
	</div>
</body>
</html>

