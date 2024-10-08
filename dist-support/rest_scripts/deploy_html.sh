#!/bin/bash
# Deploy an HTML application to the Domino server (domino/html)
# USAGE:  ./deploy_html.sh <zip> <directory>
# Parameters:
#   - <zip>.  The path to the zip file containing the application to deploy.
#   - <directory>.  The directory relative to domino/html.  The zip will be extracted into this directory.  If the directory exists, it will be recreated.

set -e

ZIP_FILE=$1
TARGET_DIR=$2

DOMINO_HTML_DIR=/local/dominodata/domino/html
TARGET_FULL=$DOMINO_HTML_DIR/$TARGET_DIR

echo "Deploying $ZIP_FILE to $TARGET_FULL"

# change permissions to ensure command runs cleanly
TIMESTAMP=`date +%Y%m%d%H%M%S`
ZIP_FILE_CHOWN=${ZIP_FILE}.${TIMESTAMP}.zip
cp "$ZIP_FILE" "$ZIP_FILE_CHOWN"
sudo chown domino.domino "$ZIP_FILE_CHOWN"

# cleanup existing application
if [ -e "$TARGET_FULL" ]; then
	sudo rm -r "$TARGET_FULL";
fi

# ensure directory exists
sudo su -c "mkdir -p '$TARGET_FULL'" - domino

# unzip the application
sudo su -c "unzip -q -d '$TARGET_FULL' '$ZIP_FILE_CHOWN'" - domino || EXIT_CODE=$?
if [ "$EXIT_CODE" -gt 1 ]; then
    # exit code 1 indicates a warning
    echo "unzip failed with exit code $EXIT_CODE";
    exit $EXIT_CODE;
fi
# fix file permissions on Windows
sudo chown -R domino.domino "$TARGET_FULL"
sudo chmod -R 744 "$TARGET_FULL"

# Cleanup
sudo rm -f "$ZIP_FILE_CHOWN"
