<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions"  prefix="fn"%>
<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean"/>
<html>
<head>
	<meta charset="utf-8" />
	<title>QueryCache</title>
</head>
<body>

<%@include file="header.jsp"%>
<script type="text/javascript">
	var g_connection_pool = [];
	var g_object_pool = null;
	var g_cluster_name = '${param.cluster}';

	function getAll() {
		$.getJSON('/api/qc/sysInfoList?cluster='+g_cluster_name, function(r) {
			var html_jvm = '';
			var html_system = '';
			var html_threads = '';
			for (var i=0; i<r.data.length; i++) {
				var s = r.data[i];
				html_jvm += '<tr><td>'+s.server+'</td><td>'+s.jvm.nProcessors+'</td><td>'+humanReadable(s.jvm.memFree)+'</td><td>';
				html_jvm += humanReadable(s.jvm.memTotal-s.jvm.memFree)+'</td><td>';
				html_jvm += ((s.jvm.memMax==-1)? 'Unlimited':humanReadable(s.jvm.memMax))+'</td></tr>';

				html_system += '<tr><td>'+s.server+'</td><td>'+parseInt(s.system.loadSystem*100)+'%</td><td>';
				html_system += parseInt(s.system.loadProcess*100)+'%</td><td>';
				html_system += humanReadable(s.system.memPhysFree)+'</td><td>';
				html_system += humanReadable(s.system.memPhysTotal)+'</td></tr>';
				// html_system += '<tr><td>Total Swap</td><td>'+humanReadable(s.system.swapTotal)+'</td></tr>';
				// html_system += '<tr><td>Free Swap</td><td>'+humanReadable(s.system.swapFree)+'</td></tr>';

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
		$('#header-title').html('<span style="color:white; font-weight: bold;">QueryCache :: System</span>');
		$('#headerMenuSystem').removeClass('btn-primary').addClass('btn-warning').attr('disabled', 'disabled');

		if (g_cluster_name == null || g_cluster_name.length == 0) {
			<c:if test="${fn:length(clusters.qcClusters) > 0}">
			window.location.href=window.location.href + "?cluster=${clusters.qcClusters[0]}";
			</c:if>
		}
		else {
			getAll();
		}
	});

</script>

<div class="container-fluid" align="center">
<div style="width: 90%; text-align:center; position: relative;">

<%--<div class="left" style="float: left; width: 500px; position: relative;">--%>
<div class="left" style="width: 33%;">
	<H2>Jvm Process Info</H2>
	<table class="table table-striped small" id="JVMInfo" style="width: 95%">
		<thead>
			<tr><th>server</th><th>#processors</th><th>mem free</th><th>mem used</th><th>mem max</th></tr>
		</thead>
		<tbody>
		</tbody>
	</table>
</div>

<%--<div style="float: right; width: 500px; position: relative;">--%>
<div class="right" style="width: 33%;">
	<H2>Thread Info</H2>
	<table class="table table-striped small" id="ThreadInfo" style="width: 95%">
		<thead>
			<tr><th>server</th><th>threads</th><th>active query thread</th></tr>
		</thead>
		<tbody>
		</tbody>
	</table>
</div>

<%--<div style="display: inline; width: 500px; position: relative;">--%>
<div class="center" style="width: 34%; left:33%;">
	<H2>System Info</H2>
	<table class="table table-striped small" id="SystemInfo" style="width: 95%">
		<thead>
			<tr><th>server</th><th>system load</th><th>querycache load</th><th>phy.mem free</th><th>phy.mem total</th></tr>
		</thead>
		<tbody>
		</tbody>
	</table>
</div>

</div>
</div>
</body>
</html>
