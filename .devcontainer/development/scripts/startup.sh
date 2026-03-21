#!/bin/bash
set -e

echo "=== Drugref 2026 Dev Container Startup ==="

echo "Building Drugref2026 WAR..."
cd /workspace
mvn -B -DskipTests clean package

echo "Deploying WAR to Tomcat..."
cp /workspace/target/drugref2.war /usr/local/tomcat/webapps/drugref2.war

echo "Starting Tomcat with JPDA debug on port 8000..."
exec catalina.sh jpda run
