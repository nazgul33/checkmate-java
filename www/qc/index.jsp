<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions"  prefix="fn"%>
<jsp:useBean id="clusters" scope="request" class="com.beans.ClustersBean"/>
<html>
<head>
    <meta charset="utf-8" />
    <title>CheckMate :: QueryCache :: Queries</title>
</head>
<body>
<%@include file="header.jsp"%>
<link rel="stylesheet" type="text/css" href="/ladda/ladda-themeless.min.css">
<script src="/ladda/spin.min.js"></script>
<script src="/ladda/ladda.min.js"></script>
<%@include file="queries.funcs.html"%>
<script type="text/javascript">
    var g_connection_pool = [];
    var g_object_pool = null;

    function getSystemStats(next) {
        $.getJSON('/api/qc/sysInfoList?cluster='+g_cluster_name, function(r) {
            for (var i=0; i<r.data.length; i++) {
                var s = r.data[i];
                var id='stats_'+s.server.toLowerCase();
                var stats=$('#systemstats tbody');
                var tr=$('#'+id, stats);
                if ($(tr).size() == 0) {
                    var html = '';
                    html += '<tr id="'+id+'"><td class="stats_name">'+s.server+'</td>';
                    html += '<td class="stats_mem">'+humanReadable(s.jvm.memTotal-s.jvm.memFree)+'/'+humanReadable(s.jvm.memTotal)+'</td>';
                    html += '<td class="stats_qcload"></td>';
                    html += '<td class="stats_sysload"></td>';
                    html += '<td class="stats_threads"></td>';
                    html += '<td class="stats_q1m">0</td><td class="stats_q5m">0</td></tr>'
                    $('#systemstats tbody').append(html);
                    tr = $('#systemstats tbody #'+id);
                }

                $('.stats_mem', tr).html(humanReadable(s.jvm.memTotal-s.jvm.memFree)+'/'+humanReadable(s.jvm.memTotal));
                $('.stats_qcload', tr).html(parseInt(s.system.loadProcess*100)+'%');
                $('.stats_sysload', tr).html(parseInt(s.system.loadSystem*100)+'%');
                $('.stats_threads', tr).html(s.threads.totalThreads);
            }
            if (next!=null) next();
        });
    }

    function getMetrics() {
        $.getJSON('/api/metrics/metrics', function(r) {
            var svrPrefix='queries.'+g_cluster_name+'.';
            for (var mName in r.meters) {
                if (mName.startsWith(svrPrefix)) {
                    var metric = r.meters[mName];
                    var svrName=mName.substr(svrPrefix.length);
                    var id='stats_'+svrName.toLowerCase();
                    var tr=$('#systemstats tbody #'+id);
                    $('.stats_q1m', tr).html(parseInt(metric.m1_rate*60));
                    $('.stats_q5m', tr).html(parseInt(metric.m5_rate*300));
                }
            }
        });
    }

    $(function() {
        $('#header-title').html('QueryCache :: Dashboard</span>');
        $('#headerMenuDashboard').removeClass('btn-primary').addClass('btn-warning').attr('disabled', 'disabled');

        if (g_cluster_name == null || g_cluster_name.length == 0) {
            <c:if test="${fn:length(clusters.qcClusters) > 0}">
            window.location.href=window.location.href + "?cluster=${clusters.qcClusters[0]}";
            </c:if>
        }
        else {
            g_rq_table = $('#queriesinflight tbody');
            g_cq_table = $('#queriescomplete tbody');
            getRunningQueries();
            initWebSocket();

            setInterval(pingWebSocket, 60*1000);
            //setInterval(getRunningQueries, 30*1000);
            setInterval(updateElapsedTimeRQ, 100);
            setInterval(getSystemStats, 10*1000);
            getSystemStats(getMetrics);
            setInterval(getMetrics, 10*1000);
        }
    });

</script>

<div class="container-fluid" align="left" width="100%">
    <H2>Server Instances</H2>
    <table class="table table-striped small table-nonfluid" id="systemstats">
        <thead><tr>
            <th>server</th>
            <th>heap usage</th>
            <th>load.qc</th>
            <th>load.system</th>
            <th>threads</th>
            <th>q/1min</th>
            <th>q/5min</th>
        </tr></thead>
        <tbody>
        </tbody>
    </table>
    <H2>In-Flight Queries</H2>
    <table class="table table-striped small" id="queriesinflight" width="100%">
        <thead><tr>
            <th class="qServer">server</th>
            <th class="qId">id</th>
            <th class="qType">type</th>
            <th class="qUser">user</th>
            <th class="qStatement">statement</th>
            <th class="qState">state</th>
            <th class="qClient">client ip</th>
            <th class="qRows">rows</th>
            <th class="qStartTime">startTime</th>
            <th class="qElapsedTime">elapsedTime</th>
            <th class="qCancel">Cancel</th>
        </tr></thead>
        <tbody>
        </tbody>
    </table>
</div>
</body>
</html>

