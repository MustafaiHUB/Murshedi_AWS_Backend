#!/bin/bash
JAR_NAME="Murshedi-0.0.1-SNAPSHOT.jar" # Make sure this matches your JAR file name

echo "Stopping Spring Boot application: $JAR_NAME"
PID=$(pgrep -f "java -jar $JAR_NAME")

if [ -z "$PID" ]; then
    echo "Application is not running."
else
    echo "Application PID: $PID. Killing process."
    kill -15 $PID # Send SIGTERM for graceful shutdown
    # Wait for a few seconds and then force kill if still running
    sleep 5
    if ps -p $PID > /dev/null; then
        echo "Process still running. Force killing."
        kill -9 $PID
    fi
    echo "Application stopped."
fi
exit 0 # Always exit with 0 for ApplicationStop, even if the app wasn't running