#!/bin/sh

GRADLE_WITNESS="1b8eaa3a788aac37ee78fa65579246d1ad268e3c8cf42cd2caeffc50b3e50056"
GRADLE_WRAPPER="0f49043be582d7a39b671f924c66bd9337b92fa88ff5951225acc60560053067"
DOWNLOADED_HASH1=''
DOWNLOADED_HASH2=''
DOWNLOAD_FILE1=$(dd if=/dev/urandom bs=64 count=1 2>/dev/null| od -t x8 -A none  | tr -d ' '\\n)
DOWNLOAD_FILE2=$(dd if=/dev/urandom bs=64 count=1 2>/dev/null| od -t x8 -A none  | tr -d ' '\\n)
unamestr=`uname`

downloadJar() {
	platform
	if [ ! -d ./rskj-core/libs ]; then
		mkdir ./rskj-core/libs
	fi
	curl https://deps.rsklabs.io/rsk-gradle-witness.jar -o ~/$DOWNLOAD_FILE1
	curl https://deps.rsklabs.io/gradle-wrapper.jar -o ~/$DOWNLOAD_FILE2
	if [ x"$PLATFORM" = x'linux' ] || [ x"$PLATFORM" = x'windows' ]; then
		DOWNLOADED_HASH1=$(sha256sum ~/${DOWNLOAD_FILE1} | cut -d' ' -f1)
		DOWNLOADED_HASH2=$(sha256sum ~/${DOWNLOAD_FILE2} | cut -d' ' -f1)
	elif [ x"$PLATFORM" = x'mac' ]; then
		DOWNLOADED_HASH1=$(shasum -a 256 ~/${DOWNLOAD_FILE1} | cut -d' ' -f1)
		DOWNLOADED_HASH2=$(shasum -a 256 ~/${DOWNLOAD_FILE2} | cut -d' ' -f1)
	fi
	if [ "$GRADLE_WITNESS" != "$DOWNLOADED_HASH1" ]; then
		rm -f ~/$DOWNLOAD_FILE1
		exit 1
	else
		mv ~/${DOWNLOAD_FILE1} ./rskj-core/libs/rsk-gradle-witness.jar
		rm -f ~/${DOWNLOAD_FILE1}
	fi
	if [ "$GRADLE_WRAPPER" != "$DOWNLOADED_HASH2" ]; then
		rm -f ~/${DOWNLOAD_FILE2}
		exit 1
	else
		mv ~/${DOWNLOAD_FILE2} ./gradle/wrapper/gradle-wrapper.jar
		rm -f ~/${DOWNLOAD_FILE2}
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
