#!/bin/bash
# Import a JSON file containing documents into database(s) using Genesis.

USAGE="./import_json_documents.sh <json-file>"


set -e

if [ "$#" -lt 1 ]; then
	echo "At least one JSON file required.  USAGE:"
	echo "    $USAGE"
	exit 1
fi

# Parameters
SOURCE_FILE=$1

# Genesis directories
GENESIS_DIR=/local/dominodata/JavaAddin/Genesis
JSON_TRIGGER_DIR=$GENESIS_DIR/json
JSON_RESPONSE_DIR=$GENESIS_DIR/jsonresponse

importJsonFile() {
	# parameters
	JSON_SOURCE_FILE=$1
	
	# validate the file
	if jq -reM '""' "$JSON_SOURCE_FILE" 2>&1; then
		echo "No errors found in file '$JSON_SOURCE_FILE'";
	else
		echo "Invalid JSON:  '$JSON_SOURCE_FILE'"
		exit 1
	fi
	
	# Ensure directories exist
	sudo mkdir -p "$JSON_TRIGGER_DIR"
	sudo mkdir -p "$JSON_RESPONSE_DIR"
	sudo chown domino.domino "$JSON_TRIGGER_DIR"
	sudo chown domino.domino "$JSON_RESPONSE_DIR"
	
	# extract the file name
	JSON_BASE_NAME=`basename -s .json "$JSON_SOURCE_FILE"`
	# build a unique target name and path
	TIMESTAMP=`date +%Y%m%d%H%M%S`
	JSON_TARGET_NAME="${TIMESTAMP}_${JSON_BASE_NAME}.json"
	JSON_TARGET_FILE=$JSON_TRIGGER_DIR/$JSON_TARGET_NAME
	
	# Start operation
	sudo chown domino.domino "$JSON_SOURCE_FILE"
	sudo cp "$JSON_SOURCE_FILE" "$JSON_TARGET_FILE"
	echo "Starting import:  $JSON_TARGET_FILE"
	
	# Wait for operation to complete
	ALLOWED_RETRIES=3
	RETRIES=0
	while [ -e $JSON_TARGET_FILE ]; # && "$RETRIES" -lt "$ALLOWED_RETRIES" ];
	do
		sleep 1;
		RETRIES=$((RETRIES+1));
	done
	if [ "$RETRIES" -ge "$ALLOWED_RETRIES" ]; then
		echo "Timeout on import attempt.";
		exit 1;
	fi
	
	# check response
	JSON_RESPONSE_FILE="$JSON_RESPONSE_DIR/$JSON_TARGET_NAME"
	RESPONSE=$(cat "$JSON_RESPONSE_FILE" | tr -d '\n')
	if [ "$RESPONSE" = "OK" ]; then
		echo "Import successful."
	else
		echo "Deployment failed:  '$RESPONSE'"
		exit 1
	fi
}

importJsonFile "$SOURCE_FILE"