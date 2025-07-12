@echo off
SET BASE_DIR=%~dp0
SET MAVEN_WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper
SET JAVA_CMD=java
%JAVA_CMD% -cp "%MAVEN_WRAPPER_DIR%\maven-wrapper.jar" ^
    -Dmaven.wrapper.version=3.9.6 ^
    -Dmaven.multiModuleProjectDirectory=%BASE_DIR% ^
    org.apache.maven.wrapper.MavenWrapperMain %*
