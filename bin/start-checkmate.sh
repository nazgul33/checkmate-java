#!/bin/bash

bin=`dirname "$0"`
bin=`cd "${bin}/.."; pwd`

export CM_HOME="${bin}"

CM_LOG_DIR=${CM_HOME}/logs
CM_CONF_DIR=${CM_HOME}/conf
CM_PID_DIR=${CM_HOME}/pid

if [ ! -d ${CM_LOG_DIR} ]; then
	mkdir ${CM_LOG_DIR}
	if [ "$?" != "0" ]; then
		echo "ERROR mkdir ${CM_LOG_DIR}"
		exit 1
	fi
fi
if [ ! -d ${CM_PID_DIR} ]; then
	mkdir ${CM_PID_DIR}
	if [ "$?" != "0" ]; then
		echo "ERROR mkdir ${CM_PID_DIR}"
		exit 1
	fi
fi
START_TIME=`date "+%Y%m%d_%H%M%S"`

CLASSPATH=${CM_CONF_DIR}:${CM_HOME}/lib/*:${CLASSPATH}

if [ "${JVM_ARGS}" == "" ]; then
	JVM_ARGS="-enableassertions -enablesystemassertions -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+CMSParallelRemarkEnabled -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly -Xms256m -Xmx512m"
fi
JVM_ARGS="${JVM_ARGS} -DCM_HOME=${CM_HOME}"
DEBUG_ARGS="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -verbose:gc -Xloggc:${CM_LOG_DIR}/checkmate_${START_TIME}.gc"

nohup java $JVM_ARGS -cp "$CLASSPATH" com.skplanet.checkmate.CheckMateServer "$@" > ${CM_LOG_DIR}/checkmate_${START_TIME}.out 2>&1 &
echo $! > ${CM_PID_DIR}/checkmate.pid
