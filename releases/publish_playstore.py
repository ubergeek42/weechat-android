#!/usr/bin/env python

import httplib2
import os
import sys
from apiclient.discovery import build
from oauth2client import client

#TRACK = 'production'
TRACK = 'beta'
SERVICE_ACCT_EMAIL = os.environ['GOOGLEPLAY_ACCT_EMAIL']
KEY = 'releases/google-play-key.p12'

PKG_NAME = 'com.ubergeek42.WeechatAndroid.dev'
APK = "weechat-android/build/outputs/apk/weechat-android-devrelease.apk"


def main():
    f = file(KEY)
    key = f.read()
    f.close()

    credentials = client.SignedJwtAssertionCredentials(
        SERVICE_ACCT_EMAIL,
        key,
        scope='https://www.googleapis.com/auth/androidpublisher')
    http = httplib2.Http()
    http = credentials.authorize(http)

    service = build('androidpublisher', 'v2', http=http)

    try:
        edit_request = service.edits().insert(body={}, packageName=PKG_NAME)
        result = edit_request.execute()
        edit_id = result['id']

        apk_response = service.edits().apks().upload(
            editId=edit_id,
            packageName=PKG_NAME,
            media_body=APK).execute()

        print 'Version code %d has been uploaded' % apk_response['versionCode']

        track_response = service.edits().tracks().update(
            editId=edit_id,
            track=TRACK,
            packageName=PKG_NAME,
            body={u'versionCodes': [apk_response['versionCode']]}).execute()

        print 'Track %s is set for version code(s) %s' % (
            track_response['track'], str(track_response['versionCodes']))

        commit_request = service.edits().commit(
            editId=edit_id, packageName=PKG_NAME).execute()
        print 'Edit "%s" has been committed' % (commit_request['id'])
    except client.AccessTokenRefreshError:
        print ('The credentials have been revoked/expired, please re-run the'
               ' application to re-authorize')

if __name__ == '__main__':
    if os.environ.get('TRAVIS_BRANCH', 'undefined') != 'master':
        print "Can't publish play store app for any branch except master"
        sys.exit(0)
    if os.environ.get('TRAVIS_PULL_REQUEST', None) != "false":
        print "Can't publish play store app for pull requests"
        sys.exit(0)
    main()
