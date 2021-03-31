#!/bin/sh

GRADLE_WRAPPER="0f49043be582d7a39b671f924c66bd9337b92fa88ff5951225acc60560053067"
DOWNLOADED_HASH=''
DOWNLOAD_FILE=$(mktemp)
unamestr=`uname`

trap 'rm -f "$DOWNLOAD_FILE"' EXIT INT QUIT TERM

downloadJar() {
	platform
	if [ ! -d ./rskj-core/libs ]; then
		mkdir ./rskj-core/libs
	fi
	curl https://deps.rsklabs.io/gradle-wrapper.jar -o "$DOWNLOAD_FILE"
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
