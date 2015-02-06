<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
	<meta charset="utf-8" />
	<title>QueryCache</title>
	<style type="text/css">
		@import '/bootstrap/css/bootstrap.min.css';
		@import '/css/querycache.css';
	</style>
	<script src="/js/jquery-1.11.1.min.js"></script>
	<script src="/bootstrap/js/bootstrap.min.js"></script>
	<script src="/js/utils.js"></script>
</head>
<script type="text/javascript">
	var g_running_queries = [];
	var g_rq_update = 0;
	var g_complete_queries = [];
	var g_cluster_name = '${param.cluster}';

	function getRunningQueries() {
		$.ajax( '/api/qc/runningQueries?cluster='+g_cluster_name+'&lastUpdate='+g_rq_update, {
			timeout:20000,
			success:function(data, ts, xhr) {
				var contents = '';
				g_running_queries = data.rq;
				g_rq_update = data.time;
				for (var i=0; i<g_running_queries.length; i++) {
					var q = g_running_queries[i];
					contents += '<tr><td>'+q.server+'</td><td>'+q.id+'</td><td>'
						+q.backend+'</td><td>'+q.user+'</td><td>'+q.statement+'</td><td>'
						+q.state+'</td><td>'+q.client+'</td><td>'+q.rowCnt+'</td><td>'
						+formatDate(new Date(q.startTime))
						+'</td><td><a href="'+q.cancelUrl+'">Cancel</a></td><td>'
						+((new Date()) - q.startTime)+'</td></tr>';
				}
				$('#queriesinflight tbody').html(contents);
				console.log("rq updated");
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
				contents += '<tr><td>'+q.server+'</td><td>'+q.id+'</td><td>'
					+q.backend+'</td><td>'+q.user+'</td><td>'+q.statement+'</td><td>'
					+q.state+'</td><td>'+q.client+'</td><td>'+q.rowCnt+'</td><td>'
					+formatDate(new Date(q.startTime))+'</td><td>'
					+(q.endTime - q.startTime)+'</td></tr>';
			}
			$('#queriescomplete tbody').html(contents);
		});
	}

	$(function() {
		if (g_cluster_name.length > 0) {
			getRunningQueries();
			getCompleteQueries();
			setInterval(getCompleteQueries, 10000);
		}
	});

</script>
<body>

<%@include file="header.jsp"%>

<H1>QueryCache : Queries</H1>

<H2>In-Flight Queries</H2>
<div class="container-fluid">
	<table class="table table-striped" id="queriesinflight">
		<thead><tr>
			<th>server</th><th>id</th><th>type</th><th>user</th><th>statement</th><th>state</th><th>client ip</th><th>rows</th><th>startTime</th><th>elapsedTime</th><th>Cancel</th>
		</tr></thead>
		<tbody>
		</tbody>
	</table>
</div>

<H2>Complete Queries</H2>
<div class="container-fluid">
	<table class="table table-striped" id="queriescomplete">
		<thead><tr>
			<th>server</th><th>id</th><th>type</th><th>user</th><th>statement</th><th>state</th><th>client ip</th><th>rows</th><th>startTime</th><th>elapsedTime</th>
		</tr></thead>
		<tbody>
		</tbody>
	</table>
</div>
</body>
</html>

