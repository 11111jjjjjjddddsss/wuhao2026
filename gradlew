#!/bin/sh
# Gradle startup script for UNIX.
# Project: 农技千问

APP_HOME=$( cd "$( dirname "$0" )" && pwd )
JAVA_EXE=java
if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
fi
exec "$JAVA_EXE" -Dfile.encoding=UTF-8 -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
