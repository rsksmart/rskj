#!/bin/sh

GRADLE_WRAPPER="575098db54a998ff1c6770b352c3b16766c09848bee7555dab09afc34e8cf590"
DOWNLOADED_HASH=''
DOWNLOAD_FILE=$(mktemp)
unamestr=`uname`

trap 'rm -f "$DOWNLOAD_FILE"' EXIT INT QUIT TERM
echo $CIRCLE_INTEGRATIONS_TOKEN
 
downloadJar() {
	platform
	if [ ! -d ./rskj-core/libs ]; then
		mkdir ./rskj-core/libs
	fi
	curl https://deps.rsklabs.io/gradle-wrapper-7.4.2.jar -o "$DOWNLOAD_FILE"
	if [ x"$PLATFORM" = x'linux' ] || [ x"$PLATFORM" = x'windows' ]; then
		DOWNLOADED_HASH=$(sha256sum "$DOWNLOAD_FILE" | cut -d' ' -f1)
	elif [ x"$PLATFORM" = x'mac' ]; then
		DOWNLOADED_HASH=$(shasum -a 256 "$DOWNLOAD_FILE" | cut -d' ' -f1)
	fi
	if [ "$GRADLE_WRAPPER" != "$DOWNLOADED_HASH" ]; then
		exit 1
	else
		mv "$DOWNLOAD_FILE" ./gradle/wrapper/gradle-wrapper.jar
	fi
}

platform() {
	case $unamestr in
	Linux)	PLATFORM='linux' ;;
	Darwin)	PLATFORM='mac' ;;
	*MINGW*)
		PLATFORM='windows'
		;;
	*)	printf "\e[1m\e[31m[ ERROR ]\e[0m UNRECOGNIZED PLATFORM"
		exit 2
		;;
	esac
}

downloadJar

exit 0
