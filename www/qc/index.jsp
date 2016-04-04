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
<script type="text/javascript">
    var g_cluster_name = '${param.cluster}';
    var g_rq = {};
    var g_cq = [];
    var g_rq_table = null;

    function cancelQuery(q, l) {
        l.start();
        q.cancelling = true;
        console.log("cancel : " + q.cancelUrl);
        $.getJSON(q.cancelUrl, function(result) {
            l.stop();
            var resultTxt;
            var timeoutVal;
            if (result.result == 'ok') {
                resultTxt = 'Cancelled';
                timeoutVal = 3000;
            }
            else {
                resultTxt = 'Error';
                timeoutVal = 10000;
            }
            $('#'+q.cuqId + ' .q-cancel a .ladda-label', g_rq_table).text(resultTxt);
            setTimeout( function() {
                delete(g_rq[q.cuqId]);
                $('#'+q.cuqId).remove();
            }, timeoutVal);
        });
    }

    function addRunningQuery(q) {
        q.cancelling = false;
        var qTr = $('#' + q.cuqId);
        if (qTr.length) {
            // if the query exists, update
            $('.q-state', qTr).html(q.state);
            $('.q-rowcnt', qTr).html(q.rowCnt);
        }
        else {
            var contents = '';
            // insert
            contents += '<tr id="' + q.cuqId + '"><td class="qServer">'+q.server
                    +'</td><td class="qId">'+q.id
                    +'</td><td class="qType">'+q.backend
                    +'</td><td class="qUser">'+q.user
                    +'</td><td class="qStatement">'+q.statement
                    +'</td><td class="qState">'+q.state
                    +'</td><td class="qClient">'+q.client
                    +'</td><td class="qRows">'+q.rowCnt
                    +'</td><td class="qStartTime">'+formatDate(new Date(q.startTime))
                    +'</td><td class="qElapsedTime">'+(((new Date()) - q.startTime)/1000).toFixed(1)
                    +'</td><td class="qCancel"><a href="#" class="btn btn-primary btn-xs ladda-button" data-style="zoom-in" data-size="xs" data-spinner-size="28" data-spinner-color="#ffffff" data-cuqId="'+ q.cuqId+'"><span class="ladda-label">Cancel</span></a>'+'</td></tr>';
//						+'</td><td class="q-cancel"><a href="#" onclick="cancelQuery(\''+q.cuqId+'\',\''+q.cancelUrl+'\');">Cancel</a>'+'</td></tr>';
            g_rq[q.cuqId] = q;
            $(g_rq_table).append(contents);
            $('#'+q.cuqId + ' .qCancel a', g_rq_table).click(function(e) {
                e.preventDefault();
                cuqId = this.getAttribute("data-cuqId");
                var q = g_rq[cuqId];
                if (q) {
                    var l = Ladda.create(this);
                    cancelQuery(q, l);
                }
            });
        }
    }

    function updateElapsedTimeRQ() {
        $('.qElapsedTime', g_rq_table).each(function () {
            var t = Number($(this).text());
            $(this).text((t+0.1).toFixed(1));
        });
    }

    function removeRunningQuery(cuqId) {
        $('#'+cuqId, g_rq_table).remove();
        delete(g_rq[cuqId]);
    }

    function getRunningQueries() {
        $.ajax( '/api/qc/runningQueries?cluster='+g_cluster_name, {
            timeout:20000,
            success:function(r, ts, xhr) {
                var rqmap = {};
                for (var i=0; i<r.data.length; i++) {
                    var q = r.data[i];
                    addRunningQuery(q);
                    rqmap[q.cuqId] = q;
                }

                // remove disappeared query
                for (var cuqId in g_rq) {
                    if (g_rq[cuqId].cancelling == true)
                        continue;

                    if (!(cuqId in rqmap)) {
                        removeRunningQuery(cuqId);
                    }
                }
//				setTimeout(getRunningQueries, 0);
            },
            error:function(xhr, ts, err) {
//				setTimeout(getRunningQueries, 10000);
            }
        });
    }

    var g_websocket = null;
    function initWebSocket() {
        if (g_websocket != null) {
            g_websocket.onclose = function() {};
            g_websocket.close();
        }

        g_websocket = new WebSocket('ws://'+window.location.host+'/api/qc/websocket');
        g_websocket.onopen = function() {
            console.log('websocket connected');
            // subscribe to a cluster
            var subscribe = {'request':'subscribe', 'channel':'cluster', 'data':g_cluster_name};
            this.send(JSON.stringify(subscribe));
        };
        g_websocket.onmessage = function(e) {
            var msg = JSON.parse(e.data);
            try {
                switch (msg.msgType) {
                    case 'runningQueryAdded':
                    case 'runningQueryUpdated':
                        addRunningQuery(msg.query);
                        break;
                    case 'runningQueryRemoved':
                        if (g_rq[msg.cuqId].cancelling != true)
                            removeRunningQuery(msg.cuqId);
                        break;
                    case 'pong':
                        break;
                    default:
                        console.log('unknown ws msg ' + e.data);
                        this.close();
                        break;
                }
            }
            catch (ex) {
                console.log('error parsing msg',msg, ex);
                this.close();
            }
        };
        g_websocket.onclose = function() {
            console.log('websocket closed');
            g_websocket = null;
            setTimeout(pingWebSocket, 5*1000);
        };
    }

    function pingWebSocket() {
        if (g_websocket == null) {
            initWebSocket();
        }
        else {
            var ping = {'request':'ping'};
            g_websocket.send(JSON.stringify(ping));
        }
    }

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
            getRunningQueries();
            initWebSocket();

            getSystemStats(getMetrics);
            setInterval(pingWebSocket, 60*1000);
            //setInterval(getRunningQueries, 30*1000);
            setInterval(updateElapsedTimeRQ, 100);
            setInterval(getSystemStats, 10*1000);
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
            <th>statement</th>
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

