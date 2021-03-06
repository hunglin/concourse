#!/usr/bin/env bash

#####################################################################
###  Script to make a self-extracting install and uggrade scripts ###
#####################################################################
# This script should ONLY be invoked from the Gradle installer task!

# Gradle passes the version number of the current build to this script
# for uniformity in naming conventions.
VERSION=$1

# This script assumes that it is running from the root of the concourse-server
# project
DISTS="build/distributions"
cd $DISTS
unzip concourse-server*zip
cd - >> /dev/null

SCRIPT_NAME=".update"
SCRIPT="$DISTS/concourse-server/$SCRIPT_NAME"

# We dynamically create an "update" script that copies certain files from
# the new distribution to the current install directory. Afterwards, the
# update script will start the server and run the upgrade task
cat << EOF > $SCRIPT
#!/usr/bin/env bash

# --- check if we should upgrade by detecting if the directory above is an
# --- installation diretory
files=\$(ls ../lib/concourse*.jar 2> /dev/null | wc -l)

# --- copy upgrade files
if [ \$files -gt 0 ]; then 
	echo 'Upgrading Concourse Server..........................................................................'
	rm -r ../lib/
	cp -fR lib/ ../lib/
	rm -r ../licenses/ 2>/dev/null
	cp -fR licenses/ ../licenses/
	cp -R bin/ ../bin/

	# --- run upgrade task
	cd ..
	# TODO exec bin/start
	# TODO exec bin/upgrade
	# TODO exec bin/stop

	cd - >> /dev/null
fi

# -- delete the update file and installer
rm $SCRIPT_NAME
cd ..
rm concourse-server*bin

# --- delete upgrade directory
if [ \$files -gt 0 ]; then
	rm -r concourse-server
fi

exit 0
EOF

# Make update script executable
chmod +x $SCRIPT

# Create the installer package
INSTALLER="concourse-server-$VERSION.bin"
../makeself/makeself.sh --notemp $DISTS/concourse-server $INSTALLER "Concourse Server" ./$SCRIPT_NAME
chmod +x $INSTALLER
mv $INSTALLER $DISTS
cd $DISTS
rm -rf concourse-server
cd - >> /dev/null

exit 0