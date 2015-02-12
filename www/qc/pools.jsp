<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions"  prefix="fn"%>
<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean"/>
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
	var g_cluster_name = '${param.cluster}';

	function getAll() {
		$.getJSON('/api/qc/poolInfoList?cluster='+g_cluster_name, function(result) {
			var c1 = '';
			for (var i=0; i<result.length; i++) {
				var p = result[i].objPool;
				c1 += '<tr><td>'+result[i].server+'</td><td>'+p.poolSize[0]+'</td><td>'+p.poolSize[1]+
				'</td><td>'+p.poolSize[2]+'</td><td>'+p.poolSize[3]+'</td></tr>';
			}
			var c2 = '';
			for (var i=0; i<result.length; i++) {
				var p = result[i].connPoolList;
				for (var j=0; j<p.length; j++) {
					c2 += '<tr><td>'+((j==0)?result[i].server:'&nbsp;')+'</td><td>'+p[j].driver+'</td><td>'+
					p[j].free+'</td><td>'+p[j].using+'</td></tr>';
				}
			}

			$('#connpool tbody').html(c2);
			$('#objpool tbody').html(c1);
		});
	}

	$(function() {
		$('#header-title').html('<span style="color:white; font-weight: bold;">QueryCache :: Pools</span>');

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
<body>

<%@include file="header.jsp"%>

<div class="container-fluid" align="center">
<div style="width: 90%" align="left">

<div style="float: left; width: 50%;">
	<H2>Connection Pool Stats</H2>
	<table class="table table-striped small table-nonfluid" id="connpool">
		<thead><tr>
			<th>server</th><th>backend</th><th>free</th><th>in use</th>
		</tr></thead>
		<tbody>
		</tbody>
	</table>
</div>

<div style="float: right; width: 50%;">
	<H2>Object Pool Stats</H2>
	<table class="table table-striped small table-nonfluid" id="objpool">
		<thead><tr>
			<th>server</th><th>TROWSET</th><th>TROW</th><th>TCOLUMNVALUE</th><th>TSTRINGVALUE</th>
		</tr></thead>
		<tbody>
		</tbody>
	</table>
</div>

</div>
</div>

</body>
</html>

