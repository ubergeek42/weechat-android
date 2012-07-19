Weechat Android Relay Client
==================================
This is an Android Weechat Relay client.
It is currently in beta status, with most things working.  I use it daily without issue.

This requires weechat to be running somewhere and it connects to it.  It is not a standalone weechat for your phone.  If you are looking for a standalone irc client for android, you will need to look elsewhere.

##Download
####Stable version - v0.07
Either scan the QR code, or click the link to download the apk.  Click on it and choose to install. [Download Link](https://github.com/downloads/ubergeek42/weechat-android/weechat-v0.07.apk)

![Download](https://chart.googleapis.com/chart?cht=qr&chs=200x200&chl=https://github.com/downloads/ubergeek42/weechat-android/weechat-v0.07.apk)

####Latest Development Snapshot - v0.08-dev
If you're feeling adventurous, you can try the latest development version.  This is built after every commit, and while I try to keep a working build, it may fail or have major bugs.

[Get the latest development version here](http://repository-ubergeek42.forge.cloudbees.com/release/index.html)

## Installation and usage:
Please refer to the quick start guide for details:

https://github.com/ubergeek42/weechat-android/wiki/Quickstart-Guide

##Screenshots
Note these are from 0.06/0.07-dev, but it looks roughly the same.

<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/chat-channel-toggles.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/chat-channel-toggles.png" height="400px"></a>
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/preferences.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/preferences.png" height="400px"></a>

<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffers.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffers.png" height="400px"></a>
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/notifications.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/notifications.png" height="400px"></a>

## Bug Reports and Feature Requests Welcome!
Please report any bugs or feature requests here on github(See the Issues tab), or send me an email: kj@ubergeek42.com.  You can also ping me in #weechat on freenode.

Please include the Build Identifier found in the about screen if possible.

##Source Code
All of the source code is available right here on github.  For a quick introduction to setting up ant/eclipse so you can begin hacking on the code, please see:
https://github.com/ubergeek42/weechat-android/wiki/Getting-started-with-the-code

For more details about Weechat and the Relay Protocol:

* Weechat - http://www.weechat.org/
* Relay Protocol - http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html




## Changelog

#### v0.07 - June 13th, 2012
* Tab completion for nicks(tab key or search button)
* Automatic reconnection
* Stunnel support(see: [Stunnel Guide](https://github.com/ubergeek42/weechat-android/wiki/Setting-up-stunnel))
* Text size preference
* Massive performance tweak/bandwith reduction
* Other bug fixes

#### v0.06 - May 13th, 2012
* Rewrite rendering of chat messages(improved performance)
* Added about screen
* Made links in messages clickable
* Fixed bug with irc colors in topics
* Password now hidden in preferences
* Added preference for prefix alignment
* Keyboard behaves nicer in chats

#### v0.05-dev - May 11th, 2012
* Complete rewrite of the frontend
* Support Notifications
* Background service
* Message filters
* UTF-8 Support(Fixes #1)

#### v.0.04
* Skipped

#### v0.03
* Preferences for Colors/Timestamp
* Highlight support for messages
* Misc bugfixes

#### v0.02
* Colors!
* A few bugfixes

#### v0.01
* Initial Release
