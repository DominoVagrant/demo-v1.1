#!/bin/bash
# Open the zipped project and run the Gradle script as DXL Importer.
# This script assumes that the project was configured following the conventions
# in https://github.com/Moonshine-IDE/DXLImporter-Gradle-Demo.
#
# USAGE:  ./run_dxl_importer.sh <zip>
# Parameters:
#   - <zip>.  The path to the zip file containing the application to deploy.  
#             The zip is expected to contain build.gradle at the top level (no parent directory).

set -e

ZIP_FILE=$1

echo "Starting DXL Import for file $ZIP_FILE"

# change permissions to ensure command runs cleanly
TIMESTAMP=`date +%Y%m%d%H%M%S`
TMP_DIR=/tmp/dxlimporter/$TIMESTAMP
mkdir -p $TMP_DIR

# unzip and setup project
cd $TMP_DIR
unzip $ZIP_FILE || EXIT_CODE=$?
if [ "$EXIT_CODE" -gt 1 ]; then
    # exit code 1 indicates a warning
    echo "unzip failed with exit code $EXIT_CODE";
    exit $EXIT_CODE;
fi
# fix file permissions on Windows
sudo chown -R vagrant.vagrant .
sudo chmod -R 744 .
# A copy of the notes.ini file is required for the Domino build
ln -s /local/notesjava/notes.ini

# Update Domino path
DOMINO_INSTALL_PATH=/opt/hcl/domino/notes/latest/linux
REPLACE_VAR=notesInstallation
REPLACE_FILE=gradle.properties
sed -i "s:^$REPLACE_VAR=.*$:$REPLACE_VAR=$DOMINO_INSTALL_PATH:" $REPLACE_FILE

# Read default user password
PASSWORD=$(jq -r '.serverSetup | .admin | .password' /local/dominodata/setup.json)

# Run Gradle
gradle -PnotesIDPassword="$PASSWORD" clean importAll



# Cleanup
sudo rm -rf "$TMP_DIR"
