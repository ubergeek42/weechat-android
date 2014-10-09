#!/bin/bash
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ ! -z "$TRAVIS_BRANCH" ]; then
    BRANCH="$TRAVIS_BRANCH"
fi

HASH=$(git log --pretty=format:'%h' -n 1)
APK="weechat-$BRANCH-$HASH.apk"

ORIG_APK="weechat-android/build/outputs/apk/weechat-android-devrelease.apk"

if [ -z "$S3_BUCKET" ];then
    S3_BUCKET="weechat-android.ubergeek42.com"
fi

if [ -z "$S3_ACCESS_KEY" ]; then
    echo "Missing S3 Access Key(Set ENV Variable S3_ACCESS_KEY)";
    exit
fi
if [ -z "$S3_SECRET_KEY" ]; then
    echo "Missing S3 Secret Key(Set ENV Variable S3_SECRET_KEY)";
    exit
fi

contentType="application/vnd.android.package-archive"
dateValue=$(date -R)
signature=$(echo -en "PUT\n\n${contentType}\n${dateValue}\n/${S3_BUCKET}/${APK}" | openssl sha1 -hmac ${S3_SECRET_KEY} -binary | base64)
echo "Uploading ${APK}"
curl -X PUT -T "${ORIG_APK}" \
    -H "Host: s3.amazonaws.com" \
    -H "Date: ${dateValue}" \
    -H "Content-Type: ${contentType}" \
    -H "Authorization: AWS ${S3_ACCESS_KEY}:${signature}" \
    https://s3.amazonaws.com/${S3_BUCKET}/${APK}




APKURL="http://weechat-android.ubergeek42.com/$APK"


HTMLFILE="index-$BRANCH.html"
cat <<EOF > $HTMLFILE
<!DOCTYPE html>
<html>
 <head>
  <meta charset="UTF-8">
  <title>Weechat-Android Automated Builds</title>
  <script type="text/javascript">
    var _gaq = _gaq || [];
    _gaq.push(['_setAccount', 'UA-1011815-6']);
    _gaq.push(['_trackPageview']);
    (function() {
      var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
      ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
      var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
    })();
  </script>
 </head>
 <body>
  <p>
    <h2>Weechat-Android (devel) built on: $(date)</h2>
    Direct Download Link: <a href="$APKURL">$APK</a><br>
    <img src="https://chart.googleapis.com/chart?cht=qr&chs=200x200&chl=$APKURL"></p>
  <p>
    This pages provides the latest version of weechat-android(${BRANCH} branch). It has not been tested very much, but should be usable.<br>
    Older builds can be found here: <a href="http://weechat-android.ubergeek42.com/dirlisting.html">All Builds</a><br>
    Official versions, along with more information regarding this project can be found on github: <a href="http://github.com/ubergeek42/weechat-android">Github</a></br>
  </p>
 </body>
</html>
EOF


contentType="text/html"
dateValue=$(date -R)
signature=$(echo -en "PUT\n\n${contentType}\n${dateValue}\n/${S3_BUCKET}/${HTMLFILE}" | openssl sha1 -hmac ${S3_SECRET_KEY} -binary | base64)
echo "Uploading ${HTMLFILE}"
curl -X PUT -T "${HTMLFILE}" \
    -H "Host: s3.amazonaws.com" \
    -H "Date: ${dateValue}" \
    -H "Content-Type: ${contentType}" \
    -H "Authorization: AWS ${S3_ACCESS_KEY}:${signature}" \
    https://s3.amazonaws.com/${S3_BUCKET}/${HTMLFILE}