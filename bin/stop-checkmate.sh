#!/bin/bash

bin=`dirname "$0"`
bin=`cd "${bin}/.."; pwd`

export CM_HOME="${bin}"

CM_PID_DIR=${CM_HOME}/pid

if [ -f ${CM_PID_DIR}/checkmate.pid ]; then
	pid=`cat ${CM_PID_DIR}/checkmate.pid`
	echo "kill -9 ${pid}"
	kill -9 "${pid}"
	rm ${CM_PID_DIR}/checkmate.pid
fi
