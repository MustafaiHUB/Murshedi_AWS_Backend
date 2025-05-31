#!/bin/bash

# Source environment variables set by UserData
if [ -f /etc/environment ]; then
    source /etc/environment
fi

# Navigate to the app directory
APP_DIR="/opt/codedeploy-agent/deployment-root/murshedi-backend"
JAR_NAME="Murshedi-0.0.1-SNAPSHOT.jar" # Make sure this matches your JAR file name

LOG_FILE="/var/log/murshedi-backend.log"

# Create log directory if it doesn't exist and set permissions
mkdir -p /var/log/
touch $LOG_FILE
chown ec2-user:ec2-user $LOG_FILE # Or the user running the app

# Start the Spring Boot application
# The SERVER_PORT environment variable (set in /etc/environment by UserData)
# will be automatically picked up by Spring Boot.
echo "Starting Spring Boot application: $JAR_NAME"
cd $APP_DIR
nohup java -jar $JAR_NAME > $LOG_FILE 2>&1 &

# Optionally, add a small delay to give the app time to start before ValidateService runs
sleep 10