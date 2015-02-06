<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean"/>
<table class="table table-nonfluid borderless"><tr><td>
    <table class="table table-nonfluid borderless" id="clusters"><tr>
        <c:forEach var="clusterName" items="${clusters.qcClusters}">
        <td><a href="/qc/index.jsp?cluster=${clusterName}">${clusterName}</a></td>
        </c:forEach>
    </tr></table>
</td><td>
    <table class="table table-nonfluid borderless" id="header"> <tr>
		<td>PAGES</td>
		<td><a href="/qc/index.jsp?cluster=${param.cluster}">system</a></td>
		<td><a href="/qc/queries.jsp?cluster=${param.cluster}">queries</a></td>
		<td><a href="/qc/pools.jsp?cluster=${param.cluster}">pools</a></td>
	</tr></table>
</td></tr></table>
