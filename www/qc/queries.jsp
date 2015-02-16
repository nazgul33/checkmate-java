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
	var g_rq_update = 0;
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
			contents += '<tr id="' + q.cuqId + '"><td class="q-server">'+q.server
			+'</td><td class="q-id">'+q.id
			+'</td><td class="q-backend">'+q.backend
			+'</td><td class="q-user">'+q.user
			+'</td><td class="q-statement">'+q.statement
			+'</td><td class="q-state">'+q.state
			+'</td><td class="q-client">'+q.client
			+'</td><td class="q-rowcnt">'+q.rowCnt
			+'</td><td class="q-starttime">'+formatDate(new Date(q.startTime))
			+'</td><td class="q-elapsed">'+((new Date()) - q.startTime)
			+'</td><td class="q-cancel"><a href="#" class="btn btn-primary btn-xs ladda-button" data-style="zoom-in" data-size="xs" data-spinner-size="28" data-spinner-color="#ffffff" data-cuqId="'+ q.cuqId+'"><span class="ladda-label">Cancel</span></a>'+'</td></tr>';
//						+'</td><td class="q-cancel"><a href="#" onclick="cancelQuery(\''+q.cuqId+'\',\''+q.cancelUrl+'\');">Cancel</a>'+'</td></tr>';
			g_rq[q.cuqId] = q;
			$(g_rq_table).append(contents);
			$('#'+q.cuqId + ' .q-cancel a', g_rq_table).click(function(e) {
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

	function removeRunningQuery(cuqId) {
		$('#'+cuqId, g_rq_table).remove();
		delete(g_rq[cuqId]);
	}

	function getRunningQueries() {
		$.ajax( '/api/qc/runningQueries?cluster='+g_cluster_name+'&lastUpdate='+g_rq_update, {
			timeout:20000,
			success:function(data, ts, xhr) {
				var rqmap = {};
				g_rq_update = data.time;
				for (var i=0; i<data.rq.length; i++) {
					var q = data.rq[i];
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

	function getCompleteQueries()
	{
		$.getJSON('/api/qc/completeQueries?cluster='+g_cluster_name, function(result) {
			var contents = '';
			g_cq = result;
			for (var i=0; i<g_cq.length; i++) {
				var q = g_cq[i];
				contents += '<tr id="' + q.cuqId + '"><td>'+q.server+'</td><td>'+q.id+'</td><td>'
					+q.backend+'</td><td>'+q.user+'</td><td>'+q.statement+'</td><td>'
					+q.state+'</td><td>'+q.client+'</td><td>'+q.rowCnt+'</td><td>'
					+formatDate(new Date(q.startTime))+'</td><td>'
					+(q.endTime - q.startTime)+'</td></tr>';
			}
			$('#queriescomplete tbody').html(contents);
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

	$(function() {
		$('#header-title').html('QueryCache :: Queries</span>');

		if (g_cluster_name == null || g_cluster_name.length == 0) {
			<c:if test="${fn:length(clusters.qcClusters) > 0}">
				window.location.href=window.location.href + "?cluster=${clusters.qcClusters[0]}";
			</c:if>
		}
		else {
			g_rq_table = $('#queriesinflight tbody');
			getRunningQueries();
			getCompleteQueries();
			initWebSocket();

			setInterval(pingWebSocket, 60*1000);
			setInterval(getCompleteQueries, 10*1000);
			setInterval(getRunningQueries, 30*1000);
		}
	});

</script>

<div class="container-fluid" align="center">
	<div class="cmscrollbar" style="width: 90%; height: 30%; text-align:left; position: relative;">
		<H2>In-Flight Queries</H2>
		<table class="table table-striped small" id="queriesinflight">
			<thead><tr>
				<th>server</th><th>id</th><th>type</th><th>user</th><th>statement</th><th>state</th><th>client ip</th><th>rows</th><th>startTime</th><th>elapsedTime</th><th>Cancel</th>
			</tr></thead>
			<tbody>
			</tbody>
		</table>
	</div>

	<div style="width: 90%; text-align:left; position: relative;">
		<H2>Complete Queries</H2>
		<table class="table table-striped small" id="queriescomplete">
			<thead><tr>
				<th>server</th><th>id</th><th>type</th><th>user</th><th>statement</th><th>state</th><th>client ip</th><th>rows</th><th>startTime</th><th>elapsedTime</th>
			</tr></thead>
			<tbody>
			</tbody>
		</table>
	</div>
</div>
</body>
</html>

