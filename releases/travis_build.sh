#!/bin/bash

if [ -z "$DEVSTOREFILE" ]; then
    DEVSTOREFILE="releases/android.jks"
fi

if [ -z "$DEVKEYALIAS" ]; then
    DEVKEYALIAS="weechat"
fi

DEVRELEASE="1"
if [ -z "$DEVSTOREPASSWORD" ]; then
    echo "\$DEVSTOREPASSWORD must be set to build a release"
    DEVRELEASE="0"
fi

if [ -z "$DEVKEYPASSWORD" ]; then
    echo "\$DEVKEYPASSWORD must be set to build a release"
    DEVRELEASE="0"
fi

if [ "$DEVRELEASE" == "1" ]; then
    ./gradlew clean assembleDevRelease \
        -PdevStorefile="$DEVSTOREFILE" \
        -PdevStorePassword="$DEVSTOREPASSWORD" \
        -PdevKeyAlias="$DEVKEYALIAS" \
        -PdevKeyPassword="$DEVKEYPASSWORD"
else
    ./gradlew clean assembleDebug
fi

exit $?
