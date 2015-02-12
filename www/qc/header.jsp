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
	<div id="header-clusters" class="header-left" align="left">
		<a class="btn btn-primary btn-xs" href="/qc/index.jsp?cluster=${param.cluster}">system</a>&nbsp;&nbsp;&nbsp;&nbsp;
		<a class="btn btn-primary btn-xs" href="/qc/queries.jsp?cluster=${param.cluster}">queries</a>&nbsp;&nbsp;&nbsp;&nbsp;
		<a class="btn btn-primary btn-xs" href="/qc/pools.jsp?cluster=${param.cluster}">pools</a>&nbsp;&nbsp;&nbsp;&nbsp;
	</div>
	<div id="header-pages" class="header-right" align="right">
		<span style="color:white; font-weight: bold;">Clusters :</span>
		<select class="selectpicker" data-style="btn-info btn-xs" onchange="onClusterChange(this)">
			<c:forEach var="clusterName" items="${clusters.qcClusters}">
				<option value="${clusterName}" <c:if test="${clusterName.equals(param.cluster)}">selected</c:if>> ${clusterName}</option>
			</c:forEach>
		</select>
	</div>
	<div id="header-title" class="header-middle" align="center">
	</div>
	<div style="clear: both;"></div>
</div>
</div>
