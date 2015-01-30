#!/bin/bash
if [ -z $CM_HOME ]; then
    this=${0/-/} # login-shells often have leading '-' chars
    shell_exec=`basename $SHELL`
    if [ "$this" = "$shell_exec" ]; then
        # Assume we're already in CM_HOME
        interactive=1
        export CM_HOME="$(pwd)/.."
    else
        interactive=0
        while [ -h "$this" ]; do
            ls=`ls -ld "$this"`
            link=`expr "$ls" : '.*-> \(.*\)$'`
            if expr "$link" : '.*/.*' > /dev/null; then
                this="$link"
            else
                this=`dirname "$this"`/"$link"
            fi
        done

        # convert relative path to absolute path
        bin=`dirname "$this"`
        script=`basename "$this"`
        bin=`cd "$bin"; pwd`
        this="$bin/$script"

        export CM_HOME=`dirname "$bin"`
    fi
fi

# explicitly change working directory to $CM_HOME
cd $CM_HOME

echo "#### starting checkmater"
echo "CM_HOME : $CM_HOME"

CLASSPATH="$CM_HOME/lib:$CM_HOME/conf"
for jar in $CM_HOME/lib/*.jar; do
    CLASSPATH=$jar:$CLASSPATH
done
export CLASSPATH

JVMARGS=${JVMARGS-"-enableassertions -enablesystemassertions -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+CMSParallelRemarkEnabled -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly -Xms256m -Xmx512m -DCM_HOME=${CM_HOME}"}
#JVMARGS=${JVMARGS-"-enableassertions -enablesystemassertions -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+CMSParallelRemarkEnabled -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly -Xms5g -Xmx5g -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -verbose:gc -Xloggc:$CM_HOME/logs/checkmate-gc-$(date +%Y%m%d-%H%M%S).log"}

java $JVMARGS -classpath "$CLASSPATH" com.skplanet.checkmate.CheckMateServer "$@"
