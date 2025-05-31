#!/bin/bash

# Define the application directory
APP_DIR="/opt/codedeploy-agent/deployment-root/murshedi-backend"

echo "Running BeforeInstall: Cleaning up old application files from $APP_DIR"

# Remove contents of the application directory except the scripts folder itself
# This ensures a clean deployment space for the new version's files
if [ -d "$APP_DIR" ]; then
    find "$APP_DIR" -mindepth 1 ! -wholename "$APP_DIR/scripts*" -delete
    echo "Cleaned $APP_DIR"
else
    echo "Directory $APP_DIR does not exist, skipping cleanup."
fi

# It's also a good idea to create the directory if it doesn't exist,
# though CodeDeploy usually handles this for 'destination' in 'files' section.
mkdir -p $APP_DIR
chown -R ec2-user:ec2-user $APP_DIR # Or the user running the app

exit 0