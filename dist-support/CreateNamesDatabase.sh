
# Load the environment for the Vagrant user
source /home/vagrant/.bashrc # load Java installation
source /home/vagrant/.bash_profile  # load LD_LIBRARY_PATH

cd /local/notesjava
# need to have a copy of the template locally
cp /local/dominodata/pernames.ntf .
chown vagrant.vagrant pernames.ntf
PASSWORD=$(jq -r '.serverSetup | .admin | .password' /local/dominodata/setup.json)
sudo su -c "cd /local/notesjava; yes '$PASSWORD' | $JAVA_HOME/bin/java -jar ./CreateNamesDatabase.jar" - vagrant
