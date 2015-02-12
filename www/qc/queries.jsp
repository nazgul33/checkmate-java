<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions"  prefix="fn"%>
<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean"/>
<html>
<head>
	<meta charset="utf-8" />
	<title>QueryCache</title>
	<style type="text/css">
		@import '/bootstrap/css/bootstrap.min.css';
		@import '/bootstrap/css/bootstrap-select.min.css';
		@import '/ladda/ladda-themeless.min.css';
		@import '/css/querycache.css';
	</style>
	<script src="/js/jquery-1.11.1.min.js"></script>
	<script src="/bootstrap/js/bootstrap.min.js"></script>
	<script src="/bootstrap/js/bootstrap-select.min.js"></script>
	<script src="/ladda/spin.min.js"></script>
	<script src="/ladda/ladda.min.js"></script>
	<script src="/js/utils.js"></script>
</head>
<script type="text/javascript">
	var g_cluster_name = '${param.cluster}';
	var g_running_queries = {};
	var g_rq_update = 0;
	var g_complete_queries = [];

	function cancelQuery(q, l) {
		l.start();
		q.cancelling = true;
		$.getJSON(q.cancelUrl, function(result) {
			l.stop();
			setTimeout( function() {
				delete(g_running_queries[q.cuqId]);
				$('#'+q.cuqId).remove();
			}, 3000);
		});
	}

	function getRunningQueries() {
		$.ajax( '/api/qc/runningQueries?cluster='+g_cluster_name+'&lastUpdate='+g_rq_update, {
			timeout:20000,
			success:function(data, ts, xhr) {
				var contents = '';
				var rqmap = {};
				g_rq_update = data.time;
				for (var i=0; i<data.rq.length; i++) {
					var q = data.rq[i];
					q.cancelling = false;
					var qTr = $('#' + q.cuqId);
					if (qTr.length) {
						// if the query exists, update
						$('.q-state', qTr).html(q.state);
						$('.q-rowcnt', qTr).html(q.rowCnt);
					}
					else {
						// insert
						contents += '<tr id="' + q.cuqId + '"><td class="q-server">'+q.server
						+'</td><td class="q-id">'+q.id
						+'</td><td class="q-backend">'+q.backend
						+'</td><td class="q-user">'+q.user
						+'</td><td class="q-statement">'+q.statement
						+'</td><td class="q-state">'+q.state
						+'</td><td class="q-client">'+q.client
						+'</td><td class="q-rowcnt">'+q.rowCnt
						+'</td><td class="q-starttime">'+formatDate(new Date(q.startTime))
						+'</td><td class="q-elapsed">'+((new Date()) - q.startTime)
						+'</td><td class="q-cancel"><a href="#" class="btn btn-primary btn-xs ladda-button" data-style="zoom-in" data-size="xs" data-spinner-size="28" data-spinner-color="#ffffff" data-cuqId="'+ q.cuqId+'"><span class="ladda-label">Cancel</span></a>'+'</td></tr>';
//						+'</td><td class="q-cancel"><a href="#" onclick="cancelQuery(\''+q.cuqId+'\',\''+q.cancelUrl+'\');">Cancel</a>'+'</td></tr>';
						g_running_queries[q.cuqId] = q;
					}
					rqmap[q.cuqId] = q;
				}
				if (contents.length > 0) {
					$('#queriesinflight tbody').append(contents);
					$('#queriesinflight tbody .q-cancel a').click(function(e) {
						e.preventDefault();
						cuqId = this.getAttribute("data-cuqId");
						var q = g_running_queries[cuqId];
						if (q) {
							var l = Ladda.create(this);
							cancelQuery(cuqId, l);
						}
					});
				}

				// remove disappeared query
				for (var cuqId in g_running_queries) {
					if (g_running_queries[cuqId].cancelling == true)
						continue;

					if (!(cuqId in rqmap)) {
						$('#' + cuqId).remove();
						delete(g_running_queries[cuqId]);
					}
				}
				setTimeout(getRunningQueries, 0);
			},
			error:function(xhr, ts, err) {
				setTimeout(getRunningQueries, 10000);
			}
		});
	}

	function getCompleteQueries()
	{
		$.getJSON('/api/qc/completeQueries?cluster='+g_cluster_name, function(result) {
			var contents = '';
			g_complete_queries = result;
			for (var i=0; i<g_complete_queries.length; i++) {
				var q = g_complete_queries[i];
				contents += '<tr id="' + q.cuqId + '"><td>'+q.server+'</td><td>'+q.id+'</td><td>'
					+q.backend+'</td><td>'+q.user+'</td><td>'+q.statement+'</td><td>'
					+q.state+'</td><td>'+q.client+'</td><td>'+q.rowCnt+'</td><td>'
					+formatDate(new Date(q.startTime))+'</td><td>'
					+(q.endTime - q.startTime)+'</td></tr>';
			}
			$('#queriescomplete tbody').html(contents);
		});
	}

	$(function() {
		$('#header-title').html('<span style="color:white; font-weight: bold;">QueryCache :: Queries</span>');

		if (g_cluster_name == null || g_cluster_name.length == 0) {
			<c:if test="${fn:length(clusters.qcClusters) > 0}">
				window.location.href=window.location.href + "?cluster=${clusters.qcClusters[0]}";
			</c:if>
		}
		else {
			getRunningQueries();
			getCompleteQueries();
			setInterval(getCompleteQueries, 10000);
		}
	});

</script>
<body>

<%@include file="header.jsp"%>

<div class="container-fluid" align="center">
	<div style="width: 90%; text-align:left; position: relative;">
		<H2>In-Flight Queries</H2>
		<table class="table table-striped small" id="queriesinflight">
			<thead><tr>
				<th>server</th><th>id</th><th>type</th><th>user</th><th>statement</th><th>state</th><th>client ip</th><th>rows</th><th>startTime</th><th>elapsedTime</th><th>Cancel</th>
			</tr></thead>
			<tbody>
			</tbody>
		</table>

		<H2>Complete Queries</H2>
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

