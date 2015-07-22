<link rel="stylesheet" type="text/css" href="/bootstrap/css/bootstrap.min.css">
<link rel="stylesheet" type="text/css" href="/bootstrap/css/bootstrap-select.min.css">
<link rel="stylesheet" type="text/css" href="/css/querycache.css">
<script src="/js/jquery-1.11.1.min.js"></script>
<script src="/bootstrap/js/bootstrap.min.js"></script>
<script src="/bootstrap/js/bootstrap-select.min.js"></script>
<script src="/js/utils.js"></script>
<script type="text/javascript">
	function onClusterChange(sel) {
		<c:set var="path" value="${pageContext.request.requestURI}" />
		window.location.href = "${path}?cluster=" + sel.value;
	}

	$(function() {
		$('.selectpicker').selectpicker();
	});
</script>

<div class="container-fluid" align="center" style="padding: 10px; background-color: #285e8e;">
<div id="header" style="width: 90%;">
	<div id="header-pages" class="header-left" align="left">
		<a id="headerMenuSystem" class="btn btn-primary btn-xs" href="/qc/index.jsp?cluster=${param.cluster}">system</a>&nbsp;&nbsp;&nbsp;&nbsp;
		<a id="headerMenuQueries" class="btn btn-primary btn-xs" href="/qc/queries.jsp?cluster=${param.cluster}">queries</a>&nbsp;&nbsp;&nbsp;&nbsp;
		<a id="headerMenuPools" class="btn btn-primary btn-xs" href="/qc/pools.jsp?cluster=${param.cluster}">pools</a>&nbsp;&nbsp;&nbsp;&nbsp;
	</div>
	<div id="header-clusters" class="header-right" align="right">
		<span style="color:white; font-weight: bold;">Clusters :</span>
		<select class="selectpicker" data-style="btn-info btn-xs" onchange="onClusterChange(this)">
			<c:forEach var="clusterName" items="${clusters.qcClusters}">
				<option value="${clusterName}" <c:if test="${clusterName.equals(param.cluster)}">selected</c:if>> ${clusterName}</option>
			</c:forEach>
		</select>
	</div>
	<div class="header-middle" align="center">
		<span style="color:white; font-weight: bold;">CheckMate ::</span>
		<span id="header-title" style="color:white; font-weight: bold;"></span>
	</div>
	<div style="clear: both;"></div>
</div>
</div>
