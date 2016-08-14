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
	var g_cluster_name = '${param.cluster}';

	function getAll() {
		$.getJSON('/api/qc/poolInfoList?cluster='+g_cluster_name, function(r) {
			var c1 = '';
			for (var i=0; i<r.data.length; i++) {
				var p = r.data[i].objPool;
				c1 += '<tr><td>'+r.data[i].server+'</td><td>'+p.poolSize[0]+'</td><td>'+p.poolSize[1]+
				'</td><td>'+p.poolSize[2]+'</td><td>'+p.poolSize[3]+'</td></tr>';
			}
			var c2 = '';
			for (var i=0; i<r.data.length; i++) {
				var p = r.data[i].connPoolList;
				for (var j=0; j<p.length; j++) {
					c2 += '<tr><td>'+((j==0)?r.data[i].server:'&nbsp;')+'</td><td>'+p[j].driver+'</td><td>'+
					p[j].free+'</td><td>'+p[j].using+'</td><td>'+p[j].direct+'</td><td>'+p[j].cumulation+'</td></tr>';
				}
			}

			$('#connpool tbody').html(c2);
			$('#objpool tbody').html(c1);
		});
	}

	$(function() {
		$('#header-title').html('<span style="color:white; font-weight: bold;">QueryCache :: Pools</span>');
		$('#headerMenuPools').removeClass('btn-primary').addClass('btn-warning').attr('disabled', 'disabled');

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
<div style="width: 90%" align="left">

<div style="float: left; width: 50%;">
	<H2>Connection Pool Stats</H2>
	<table class="table table-striped small table-nonfluid" id="connpool">
		<thead><tr>
			<th>server</th><th>backend</th><th>pool free</th><th>pool used</th><th>direct</th><th>created</th>
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

