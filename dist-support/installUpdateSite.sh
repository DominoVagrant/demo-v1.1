#!/usr/bin/bash
# Script settings:
# -e:  exit the scripts if any of the commands fail
# -x:  Echo the commands
set -ex

sudo mkdir -p /opt/nsfodp/updatesite
sudo chown -R vagrant.vagrant /opt/nsfodp

yes | sudo yum install git
curl -s "https://get.sdkman.io" | bash
source "/home/vagrant/.sdkman/bin/sdkman-init.sh"
yes | sdk install maven 3.6.0  # match Moonshine-IDE
export MAVEN_HOME=/home/vagrant/.sdkman/candidates/maven/current
export PATH=${MAVEN_HOME}/bin:${PATH}
git clone https://github.com/OpenNTF/generate-domino-update-site /home/vagrant/generate-domino-update-site
cd /home/vagrant/generate-domino-update-site/generate-domino-update-site
mvn install
mvn org.openntf.p2:generate-domino-update-site:generateUpdateSite -Ddest=/opt/nsfodp/updatesite -Dsrc=/opt/hcl/domino/notes/latest/linux
#cp /vagrant/dist-support/maven_settings.xml /home/vagrant/.sdkman/candidates/maven/3.8.6/conf/settings.xml
# Instead use the global maven settings
cp /vagrant/dist-support/maven_settings.xml /home/vagrant/.m2/settings.xml

# zip the update site for later download
cd /opt/nsfodp/updatesite
zip -r /opt/nsfodp/updatesite.zip *

# Make sure jq is installed for password parsing in the run_nsfodp.sh script
#sudo yum install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
sudo yum install -y jq

# Copy the script
cp /vagrant/dist-support/rest_scripts/run_nsfodp.sh /opt/nsfodp/run_nsfodp.sh 
