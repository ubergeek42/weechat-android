#!/bin/sh

# this script actually works on windows (git bash) too! wow
# inkscape must be in the path, e.g. $ PATH=$PATH:/c/Program\ Files/Inkscape

# we are not optimizing the pngs here as this will be done by aapt

# launcher icon: 48 dp (48 inner)
# action bar:    32 dp (24 inner)
#   enabled:  0.8 opacity (204 / #cc alpha)
#   disabled: 0.3 opacity (77 / #4d alpha)
# notification:  24 dp (22 inner)

#               MDPI  HDPI    XHDPI   XXHDPI  XXXHDPI
#                1x   1.5x     2x       3x       4x
#               -------------------------------------
# LAUNCHER       48    72      96      144      192
# ACTION BAR     32    48      64       96      128
# NOTIFICATION   24    36      48       72       96
# BIG (200dp)   200   300     400      600      800

# BIG_ICONS="ic_big_connected ic_big_connecting ic_big_disconnected"
# ICONS="ic_bell ic_bell_cracked ic_users ic_send ic_tab ic_send_disabled ic_tab_disabled"
# NOTIFICATIONS="ic_connected ic_connecting ic_disconnected ic_hot"

NOTIFICATIONS="
ic_notification_main

ic_notification_hot

ic_notification_uploading_anim1
ic_notification_uploading_anim2
ic_notification_uploading_anim3
ic_notification_uploading_anim4
ic_notification_uploading_anim5
ic_notification_uploading_anim6
ic_notification_uploading_anim7

ic_notification_upload_done
ic_notification_upload_cancelled
"

render()
{
    inkscape --file="$1.svg" \
             --export-png="../res/drawable-$2/$1.png" \
             --export-area-page \
             --export-width=$3 \
             --export-height=$4
}

for FILE in $NOTIFICATIONS; do
    echo ""
    echo Processing notification: ${FILE}.svg
    render $FILE mdpi 24 24
    render $FILE hdpi 36 36
    render $FILE xhdpi 48 48
    render $FILE xxhdpi 72 72
    render $FILE xxxhdpi 96 96
done

# for FILE in $BIG_ICONS; do
#     echo processing big icon: ${FILE}.svg
#     render $FILE "mdpi" 200 200
#     render $FILE "hdpi" 300 300
#     render $FILE "xhdpi" 400 400
#     render $FILE "xxhdpi" 600 600
#     render $FILE "xxxhdpi" 800 800
# done

# for FILE in $ICONS; do
#     echo processing icon: ${FILE}.svg
#     render $FILE "mdpi" 32 32
#     render $FILE "hdpi" 48 48
#     render $FILE "xhdpi" 64 64
#     render $FILE "xxhdpi" 96 96
#     render $FILE "xxxhdpi" 128 128
# done

# echo Processing ic_paste.svg
# render ic_paste "mdpi" 26 32
# render ic_paste "hdpi" 39 48
# render ic_paste "xhdpi" 52 64
# render ic_paste "xxhdpi" 78 96
# render ic_paste "xxxhdpi" 104 128