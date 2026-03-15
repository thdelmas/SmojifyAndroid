#!/bin/bash

REQUIRED_JAVA_VERSION="17.0.0"

check_java_version() {
echo "Checking Java Version"
if ! which java &>- ;
then
	echo "Java not found, please install Java first"
	exit 1
else
	JAVA_VERSION="$(java --version | head -n1 | cut -d' ' -f2)"
	echo "Java Version: $JAVA_VERSION"
	LOWEST_JAVA_VERSION="$(printf "%s\n%s\n" "$JAVA_VERSION" "$REQUIRED_JAVA_VERSION" | sort -V | head -n1)"
	if [ "$LOWEST_JAVA_VERSION" != "$REQUIRED_JAVA_VERSION" ]
	then
		echo "Please update your java version to $REQUIRED_JAVA_VERSION or later"
		exit 1
	fi
fi
}


echo "Emuji Installer"
check_java_version

echo "Downloading Spotify libs"
mkdir -pv 'app/libs'
wget 'https://github.com/spotify/android-sdk/releases/download/v0.7.2-appremote_v1.2.3-auth/spotify-app-remote-release-0.7.2.aar'
mv 'spotify-app-remote-release-0.7.2.aar' 'app/libs/'
wget 'https://github.com/spotify/android-sdk/releases/download/v0.7.1-appremote_v1.2.3-auth/spotify-auth-release-1.2.3.aar'
mv 'spotify-auth-release-1.2.3.aar' 'app/libs/'
./gradlew clean build --refresh-dependencies
echo "Done"
