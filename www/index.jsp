<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
</head>
<body>
<h1>Welcome to CheckMate</h1>
<hr>
<h2>Choose a cluster of interest</h2>
<hr>
<h3>QueryCache clusters</h2>
<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean"/>

<c:forEach var="cluster" items="${clusters.qcClusters}">
    <a href="/qc/index.jsp?cluster=${cluster}">${cluster}</a><br />
</c:forEach>
</body>
</html>
