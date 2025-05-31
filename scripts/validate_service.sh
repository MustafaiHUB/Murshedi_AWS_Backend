#!/bin/bash

# Source environment variables to get SERVER_PORT if needed, though ALB health check is primary
if [ -f /etc/environment ]; then
    source /etc/environment
fi

# Wait for a few seconds to give the service time to fully start
sleep 15 

# Check if the application is listening on the configured port (SERVER_PORT)
# This is a basic check. A more robust check would be to curl a health endpoint.
# The ALB health check configured in your CloudFormation template is the primary validation.
# This script provides an additional layer for CodeDeploy itself.

# Example: Curl a health endpoint (assuming Spring Boot Actuator is on /actuator/health)
# Adjust the port and path as needed. SERVER_PORT is from /etc/environment.
PORT_TO_CHECK=${SERVER_PORT:-8080} # Use SERVER_PORT or default to 8080

# Try up to 5 times to get a 200 OK from the health endpoint
for i in {1..5}; do
    HTTP_CODE=$(curl --silent --output /dev/stderr --write-out "%{http_code}" http://localhost:${PORT_TO_CHECK}/actuator/health)
    if [ "$HTTP_CODE" -eq 200 ]; then
        echo "Application is healthy. HTTP Code: $HTTP_CODE"
        exit 0 # Success
    fi
    echo "Attempt $i: Application not healthy yet (HTTP Code: $HTTP_CODE). Retrying in 5 seconds..."
    sleep 5
done

echo "Application failed to report healthy status after multiple attempts."
exit 1 # Failure