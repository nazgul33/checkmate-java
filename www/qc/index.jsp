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
	var g_connection_pool = [];
	var g_object_pool = null;
	var g_cluster_name = '${param.cluster}';

	function getAll() {
		$.getJSON('/api/qc/sysInfoList?cluster='+g_cluster_name, function(r) {
			var html_jvm = '';
			var html_system = '';
			var html_threads = '';
			for (var i=0; i<r.length; i++) {
				var s = r[i];
				html_jvm += '<tr><td>'+s.server+'</td><td>'+s.jvm.nProcessors+'</td><td>'+humanReadable(s.jvm.memFree)+'</td><td>';
				html_jvm += humanReadable(s.jvm.memTotal-s.jvm.memFree)+'</td><td>';
				html_jvm += ((s.jvm.memMax==-1)? 'Unlimited':humanReadable(s.jvm.memMax))+'</td></tr>';

				html_system += '<tr><td>'+s.server+'</td><td>'+parseInt(s.system.loadSystem*100)+'%</td><td>';
				html_system += parseInt(s.system.loadProcess*100)+'%</td><td>';
				html_system += humanReadable(s.system.memPhysFree)+'</td><td>';
				html_system += humanReadable(s.system.memPhysTotal)+'</td></tr>';
				// html_system += '<tr><td>Total Swap</td><td>'+humanReadable(r.system.swapTotal)+'</td></tr>';
				// html_system += '<tr><td>Free Swap</td><td>'+humanReadable(r.system.swapFree)+'</td></tr>';

				html_threads += '<tr><td>'+s.server+'</td><td>'+s.threads.totalThreads+'</td><td>';
				html_threads += (s.threads.handlerThreads - s.threads.handlerThreadsIdle)+'</td></tr>';
				// html_threads += '<tr><td>Web Server Threads</td><td>'+s.threads.webServerThreads+'</td></tr>';
				// html_threads += '<tr><td>&nbsp - Active</td><td>'+(s.threads.webServerThreads - s.threads.webServerThreadsIdle)+'</td></tr>';
			}
			$('#JVMInfo tbody').html(html_jvm);
			$('#SystemInfo tbody').html(html_system);
			$('#ThreadInfo tbody').html(html_threads);
		});
	}

	$(function() {
		getAll();
	});

</script>
<body>

<%@include file="header.jsp"%>

<H1>QueryCache System/Process Information</H1>

<H2>Jvm Process Info</H2>
<div class="container">
	<table class="table table-bordered table-nonfluid" id="JVMInfo">
		<thead>
		<tr><th>server</th><th>#processors</th><th>mem free</th><th>mem used</th><th>mem max</th></tr>
		</thead>
		<tbody>
		</tbody>
	</table>
</div>

<H2>System Info</H2>
<div class="container">
	<table class="table table-bordered table-nonfluid" id="SystemInfo">
		<thead>
		<tr><th>server</th><th>system load</th><th>querycache load</th><th>phy.mem free</th><th>phy.mem total</th></tr>
		</thead>
		<tbody>
		</tbody>
	</table>
</div>

<H2>Thread Info</H2>
<div class="container">
	<table class="table table-bordered table-nonfluid" id="ThreadInfo">
		<thead>
		<tr><th>server</th><th>threads</th><th>active query thread</th></tr>
		</thead>
		<tbody>
		</tbody>
	</table>
</div>
</body>
</html>