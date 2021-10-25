Introduction
------------

This repository contains a vagrant configuration for spinning up a domino
server.

Prerequisites to using it include an installation of vagrant itself, the
vagrant-vbguest plugin, and virtualbox. Only the virtualbox provider
has been tested and at this time it is not recommended to use any other
vagrant provider. The vagrant configuration has been tested under Mac OS X
and Windows, and is known to have issues under Linux, which is also not
recommended to use at this time (in particular, vagrant under linux
does not support the reboot guest capability natively, and while there
is a plugin to add it
(https://github.com/secret104278/vagrant_reboot_linux/tree/master) it
didn't seem to work reliably).

Download and install virtualbox on your chosen platform:

	https://www.virtualbox.org/wiki/Downloads


Next, download and install vagrant on your chosen platform:

	https://www.vagrantup.com/downloads


Once vagrant has been installed, provision the vbguest plugin by running:

	vagrant plugin install vagrant-vbguest


Clone this git repository onto your system:

	git clone git@github.com:DominoVagrant/domino.git


You will need to supply the Domino installer and optional fix pack files
yourself (eg, Domino_12.0_Linux_English.tar, Domino_1101FP2_Linux.tar).
Copy these files into or create symlinks to them in the
vagrant_cached_domino_mfa_files subdirectory of the git checkout.

At this point, you can execute 'vagrant up' in the git checkout directory
to spin up a vm instance, or use the utility scripts
vagrant_up.sh/vagrant_up.ps1 to create a log file with the initialization
output in addition to showing on the screen.

Once the system has been provisioned, you can use 'vagrant ssh' to access
it, or again the utility scripts vagrant_ssh.sh/vagrant_ssh.ps1 to create
a log file of the ssh session.

View the contents of the dist-support/CommandHelp.text for more details.
This file will also be displayed followed each vagrant up operation for
your continued reference.


Provisioning details
--------------------

The vagrant configuration begins with a CentOS 7 box image and initially
installs/configures the virtual box guest tools on it.

It then proceeds to install/configure the domino server [need more detail here
from someone familiar with this process].

