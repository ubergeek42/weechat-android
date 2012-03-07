Weechat Android Relay Client v0.03
==================================
This is an Android Weechat Relay client.
It should be treated as pre-alpha software, with no expectations of functionality.(However, in my limited tests it seems to mostly work)

###Download
USE AT YOUR OWN RISK! This is alpha software and I am not responsible if your device bursts into flames, you lose all of your data, or if anything else bad happens.

Either scan the QR code, or click the link to download the apk.  Click on it and choose to install. [Download Link](https://github.com/downloads/ubergeek42/weechat-android/weechat-0.03.apk)

![Download](https://chart.googleapis.com/chart?cht=qr&chs=200x200&chl=https://github.com/downloads/ubergeek42/weechat-android/weechat-0.03.apk)

### Bug Reports and Feature Requests Welcome!
Please report any bugs or feature requests here on github, or send me an email: kj@ubergeek42.com

###Basic Usage
At the main screen, press menu, then choose preferences.
Configure your hostname, port, and password.  You can also choose to connect automatically next time the app runs, or enable colors(May be a little slow at times).

Then press Menu->Connect to connect to the server. If successful you will see a list of your weechat buffers.  Clicking on a buffer opens it in a new tab.  A buffer currently displays the 50 most recent lines.  I plan to make this configurable in the future(More is slower).

To see the nicklist for the current buffer, press menu, then choose nicklist.

The back button exits(and disconnects).  There is currently no support for running in the background.


###Source Code
There are 3 basic parts to this, the android application, the java library which provides most of
the functionality of connecting to the weechat relay server, and an example client showing the basic
features of that library.

* weechat - An eclipse project to build the Android sample application(May require modifying the library path to include weechat-relay.jar from weechat-relay, you may also need slf4j/slf4j-android)
* weechat-relay - A library implementing the Weechat Relay Protocol
* weechat-relay-example - A simple example program that makes use of the weechat-relay library

You can build these projects by running 'ant' from this directory. If you wish to build them individually, you must build weechat-relay(the library) first.

To build the android application, you will need to 'cd weechat', then run 'android update project --path .' before running ant.

For more details about Weechat and the Relay Protocol:

* Weechat - http://www.weechat.org/
* Relay Protocol - http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html

###Screenshots
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/chat-channel.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/chat-channel.png" width="400px"></a>

<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/preferences.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/preferences.png" height="400px"></a>
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffers-tab.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffers-tab.png" height="400px"></a>
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffer-colors.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffer-colors.png" height="400px"></a>

### Changelog
#### v0.03
* Preferences for Colors/Timestamp
* Highlight support for messages
* Misc bugfixes

#### v0.02
* Colors!
* A few bugfixes

#### v0.01
* Initial Release
