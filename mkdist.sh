#!/bin/bash
set -e

version=`mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | egrep '^[0-9]*\.[0-9]*\.[0-9]'`
echo "building checkmate version ${version}"
mvn clean package install -DskipTests

distdir=checkmate-dist
echo "constructing directory structure..  ${distdir}"
rm -rf ${distdir}
mkdir ${distdir}
mkdir ${distdir}/logs
cp -RL bin conf www ${distdir}
mkdir ${distdir}/lib
deps=`mvn dependency:build-classpath | egrep "/.*\.jar" | awk -F':' '{ for (i = 1; i <= NF; i++) { print $i }}' | egrep "/.*\.jar"`
for f in $deps; do
  if [ -e $f ]; then
    cp -RLv $f ${distdir}/lib/
  else
    echo $f
  fi
done
cp -v target/checkmate-${version}.jar ${distdir}/lib/

tar czf checkmate-dist.tgz ${distdir}
