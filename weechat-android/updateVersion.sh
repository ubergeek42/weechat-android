#!/bin/bash
ID=`git describe`
cat <<EOF > res/values/generated.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_build_id">$ID</string>
</resources>
EOF
