#!/bin/sh
# Apache Maven Wrapper startup script
# Licensed under the Apache License, Version 2.0

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

BASEDIR=$(cd "$(dirname "$0")"; pwd -P)

JAVA_EXEC="${JAVA_HOME}"/bin/java
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_EXEC" ]; then
  JAVA_EXEC=$(command -v java)
fi

if [ ! -x "$JAVA_EXEC" ]; then
  echo "ERROR: Java executable not found. Please set JAVA_HOME." >&2
  exit 1
fi

MAVEN_CONFIG="${MAVEN_CONFIG:-}"

WRAPPER_JAR="$BASEDIR/.mvn/wrapper/maven-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: Maven wrapper JAR missing: $WRAPPER_JAR" >&2
  exit 1
fi

exec "$JAVA_EXEC" -Dmaven.multiModuleProjectDirectory="$BASEDIR" -classpath "$WRAPPER_JAR" "$WRAPPER_LAUNCHER" $MAVEN_CONFIG "$@"
