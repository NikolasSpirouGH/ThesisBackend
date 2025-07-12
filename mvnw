#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper script for UNIX
# ----------------------------------------------------------------------------
MVN_VERSION="3.9.6"
BASE_DIR=$(dirname "$0")
MAVEN_WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
JAVA_CMD="java"
$JAVA_CMD -cp "$MAVEN_WRAPPER_DIR/maven-wrapper.jar" \
    -Dmaven.wrapper.version=$MVN_VERSION \
    -Dmaven.multiModuleProjectDirectory=$BASE_DIR \
    org.apache.maven.wrapper.MavenWrapperMain "$@"
