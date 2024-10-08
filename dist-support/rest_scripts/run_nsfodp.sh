#!/bin/bash
# Open the zipped project and run the Maven script as NSFDP
# This script assumes that the project was configured following the conventions
# in the Domino Visual Editor and Domino On Disk Project templates in Moonshine-IDE
#
# USAGE:  ./run_nsfodp.sh <zip>
# Parameters:
#   - <zip>.  The path to the zip file containing the application to deploy.  
#             The zip is expected to contain pom.xml at the top level (no parent directory).

set -e

ZIP_FILE=$1
# TODO:  move this to parameter?
DATABASE=nsfs/nsf-moonshine/target/nsf-moonshine-domino-1.0.0.nsf

echo "Starting NSFODP for file $ZIP_FILE"

# change permissions to ensure command runs cleanly
TIMESTAMP=`date +%Y%m%d%H%M%S`
TMP_DIR=/tmp/nsfodp/$TIMESTAMP
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

# Read default user password
PASSWORD=$(jq -r '.serverSetup | .admin | .password' /local/dominodata/setup.json)

# Run Maven
yes "$PASSWORD" | mvn clean install

# copy output file
OUTPUT_DIR=/tmp/restinterface/generated/
OUTPUT_FILE=$OUTPUT_DIR/$TIMESTAMP.nsf
mkdir -p $OUTPUT_DIR
cp "$DATABASE" "$OUTPUT_FILE"
echo "Generated Database:  '$OUTPUT_FILE'"


# Cleanup
sudo rm -rf "$TMP_DIR"
