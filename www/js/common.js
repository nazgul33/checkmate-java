var g_cluster_name = '';
var g_clusters = [];
var g_baseurl = '';

function getClusters(clusterListURL, header, cb)
{
    $.getJSON(clusterListURL, function(result) {
        var contents = '<td>CLUSTERS</td>';
        for (var i=0; i<result.length; i++) {
            if (g_cluster_name == result[i]) {
                contents += '<td>'+result[i]+'</td>';
            }
            else {
                contents += '<td><a href="'+g_baseurl+'?cluster='+result[i]+'">'+result[i]+'</a></td>';
            }
        }
        header.find('#clusters tbody tr').html(contents);
        cb(header.html());
    });
}

function getClusterFromUrl() {
    var qmarkidx = window.location.href.lastIndexOf('?');
    if ( qmarkidx >= 0) {
        var q = window.location.href.substring(qmarkidx+1);
        qsplit = q.split('=');
        if (qsplit.length == 2 && qsplit[0] == 'cluster') {
            g_cluster_name = qsplit[1];
        }
        g_baseurl = window.location.href.substring(0, qmarkidx);
    }
    else {
        g_baseurl = window.location.href;
    }
    return g_cluster_name;
}

function getHeader(header_url, clusterListURL, cb) {
    $.ajax({
        url: header_url,
        cache:false
    }).done(function(html) {
        var header = $(html);
        header.find('#header tr td a[href]').each(function() {
            this.href = this.href + '?cluster=' + g_cluster_name;
        });
        getClusters(clusterListURL, header, cb);
    });
}

function clusterInit(clusterListURL, headerURL) {
    getClusterFromUrl();
    getHeader(headerURL, clusterListURL, function(html) {
        $('#header-div').html(html);
    });
}
