#!/bin/bash

if [ -z "$DEVSTOREFILE" ]; then
    DEVSTOREFILE="releases/android.jks"
fi

if [ -z "$DEVKEYALIAS" ]; then
    DEVKEYALIAS="weechat"
fi

DEV="1"
if [ -z "$DEVSTOREPASSWORD" ]; then
    echo "\$DEVSTOREPASSWORD must be set to build a release"
    DEV="0"
fi

if [ -z "$DEVKEYPASSWORD" ]; then
    echo "\$DEVKEYPASSWORD must be set to build a release"
    DEV="0"
fi

if [ "$DEV" == "1" ]; then
    ./gradlew --no-daemon clean :app:assembleDev \
        -PdevStorefile="$DEVSTOREFILE" \
        -PdevStorePassword="$DEVSTOREPASSWORD" \
        -PdevKeyAlias="$DEVKEYALIAS" \
        -PdevKeyPassword="$DEVKEYPASSWORD"
else
    ./gradlew clean :app:assembleDebug
fi

exit $?
