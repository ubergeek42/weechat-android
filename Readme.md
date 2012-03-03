Weechat Android Relay Client v0.01
==================================
This is the first "release" of my android relay client for weechat.
It should be treated as pre-alpha software, with no expectations of
functionality.(However, in my limited tests it seems to mostly work)

Download
--------
USE AT YOUR OWN RISK! This is alpha software and I am not responsible
if your device bursts into flames, you lose all of your data, or if
anything else bad happens.

[Link to apk](https://github.com/ubergeek42/weechat-android/raw/master/releases/weechat.apk)

Please report any bugs/feature requests here on github, or send me an email: kj@ubergeek42.com

Screenshots
-----------
![Chat View](https://github.com/ubergeek42/weechat-android/raw/master/releases/chat-channel.png)
![Buffer List](https://github.com/ubergeek42/weechat-android/raw/master/releases/buffers-tab.png)
![Preferences page](https://github.com/ubergeek42/weechat-android/raw/master/releases/preferences.png)


Source Code
-----------
There are 3 basic parts to this, the android application, the java library which provides most of
the functionality of connecting to the weechat relay server, and an example client showing the basic
features of that library.

* weechat - An eclipse project to build the Android sample application(May require modifying the library path to include weechat-relay.jar from weechat-relay, you may also need slf4j/slf4j-android)
* weechat-relay - A library implementing the Weechat Relay Protocol
* weechat-relay-example - A simple example program that makes use of the weechat-relay library

You can build these projects by running 'ant' from this directory. If you wish to build them individually, you must build weechat-relay(the library) first.

To build the android application, you will need to 'cd weechat', then run 'android update project --path .' before running ant.

For more details about Weechat and the Relay Protocol:
Weechat - http://www.weechat.org/
Relay Protocol - http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html
