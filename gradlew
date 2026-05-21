#!/usr/bin/env sh
set -e
APP_HOME="${APP_HOME:-$(cd "$(dirname "$0")" && pwd)}"
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
  echo "Downloading gradle wrapper..."
  curl -sL "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar" \
    -o "$GRADLE_WRAPPER_JAR"
fi

exec java $JAVA_OPTS \
  -classpath "$GRADLE_WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain "$@"
