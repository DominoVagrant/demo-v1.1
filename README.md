## Introduction

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

If you want to access the server from a Notes ID, create a safe ID using 
the instructions [here](#access-from-notes-client)

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


## Provisioning details

The vagrant configuration begins with a CentOS 7 box image and initially
installs/configures the virtual box guest tools on it.

It then proceeds to install/configure the domino server [need more detail here
from someone familiar with this process].

## Accessing the Domino Server

The Domino server will be started automatically when `vagrant up` completes.

### Domino Console

To access the console, run:

    vagrant ssh
    screen -r
    
### Web Interface

The web interface of the server is here:  http://localhost:8080/names.nsf

### Access from Notes Client

If you want to access the server from a Notes Client, you will need to cross-certify your ID.  To do this, first create a safe ID:
1. Open User Security:
	- MacOS:  HCL Notes > Security > User Security 
	- Windows:  File > Security > User Security
2. Select the Your Identity > Your Certificates tab
3. Run Other Actions > Export NotesID Safe ID.  Do not set a password

Copy this ID to `./dist-id-files/safe.ids` (or update `SAFE_NOTES_ID` in `Vagrantfile`), and run `vagrant up`.

Then you will need to create a connection document in your local Notes client.
1. File > Open > HCL Notes Application
2. Open names.nsf on your local machine
3. Click `Advanced` in the bottom of the left sidebar
4. Open the Connections view
5. Click New > Server Connection
	1. In the Basic tab, set `Server name` as "demo/Demo" and check the `TCP/IP` checkbox
	2. In the Advanced tab, set the `Destination server address` to "127.0.0.1:1352"
	3. Click `Save & Close`
	
Then you can open a database on the server like this:
1. File > Open > HCL Notes Application
2. Enter "demo/DEMO" as the server name
3. Select a database (like names.nsf) and click Open


### Credentials

* username: Demo Admin
* password: password
