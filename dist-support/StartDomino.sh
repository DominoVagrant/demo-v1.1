if (ps -ef | grep -v grep | grep 'domino/bin/server'); then  # server is running
  echo "Domino server is already running."
else
  TIMESTAMP=`date +%Y%m%d%H%M%S`
  DOMINO_LOG_DIR=/local/dominodata/dominolog
  DOMINO_LOG_FILE=$DOMINO_LOG_DIR/server_${TIMESTAMP}.log
  DOMINO_INPUT_FILE=$DOMINO_LOG_DIR/domino.input
  
  # Prepare input and output
  # We could change the permissions on the log directory if desired, but I felt it was better to use the same permissions as the Domino server
  sudo mkdir -p "$DOMINO_LOG_DIR"
  sudo rm -f "$DOMINO_INPUT_FILE"  # clear any existing commands
  sudo touch "$DOMINO_INPUT_FILE"
  sudo chown -R domino.domino "$DOMINO_LOG_DIR"
  
  # Run screen as vagrant, run server as domino.
  # nohup - prevent command from hangcing on prompts
  # -d -m - Start screen in detatched mode
  # -L - automatic output logging
  # Add this option after "server" to allow commands to be sent programatically (breaks screen console):  < \"$DOMINO_INPUT_FILE\"
  sudo su -c "nohup screen -d -m bash -c \"sudo su -c '/opt/hcl/domino/bin/server  | tee $DOMINO_LOG_FILE' - domino\"" - vagrant
  echo ""
  echo "#### Domino server started in screen.  It may take a couple minutes to fully start"
  echo "## Run "screen -r" to see the server console"
  echo "## Domino server output written to $DOMINO_LOG_FILE"
  #echo "## Run server command from 'screen -r' or with:  sudo su -c 'echo \"show server\" >> /local/dominodata/dominolog/domino.input' - domino"
fi
