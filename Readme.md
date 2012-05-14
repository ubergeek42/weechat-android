Weechat Android Relay Client
==================================
This is an Android Weechat Relay client.
It is currently in beta status, with most things working.  It has not been extensively tested, so it may force close or drain your battery excessively.(However, in my limited testing it seems quite fine, I use it daily)

##Download
####Stable version
Either scan the QR code, or click the link to download the apk.  Click on it and choose to install. [Download Link](https://github.com/downloads/ubergeek42/weechat-android/weechat-0.05-dev.apk)

![Download](https://chart.googleapis.com/chart?cht=qr&chs=200x200&chl=https://github.com/downloads/ubergeek42/weechat-android/weechat-0.05-dev.apk)

####Latest Development Snapshot
If you're feeling adventurous, you can try the latest development version.  This is built after every commit, and while I try to keep a working build, it may fail or have major bugs.

[Get the latest development version here](http://repository-ubergeek42.forge.cloudbees.com/release/index.html)

## Bug Reports and Feature Requests Welcome!
Please report any bugs or feature requests here on github, or send me an email: kj@ubergeek42.com.  You can also ping me in #weechat on freenode.

##Basic Usage
At the main screen, press menu, then choose preferences.
Configure your hostname, port, and password.  Some other options:

* Connect automatically
* Automatically reconnect, useful on a flakey network
* Colors in chat(may be slow depending on device)
* Timestamps can be toggled to make better use of screen space
* Messages filtered by weechat can be filtered here(e.g. irc_smart_filter)
* Play the default notification sound on highlight/private message

Then press Menu->Connect to connect to the server. If successful you will see a list of your weechat buffers.  Clicking on a buffer opens it.  A buffer currently displays the 200 most recent lines(including possible filtered lines).  I plan to make this configurable in the future(More is slower).

The nicklist is missing from this version, I'm looking at the most useful way to add it back.

The app does run in the background, however if you would like to exit it, from the buffers list menu->quit.  I've run it in the background all day with no real noticable drain on my battery.

## Requirements + Setup
* Weechat v0.3.7+
* Android 2.1+ - The only permissions needed are internet access.
* Note: If you want support for highlights(private message notifications will work otherwise), you must be using Weechat v0.3.8-dev after March 6th.

In weechat, you will need to configure the relay server.  /help relay will get you most of the way there.  Basically you will need to run:

    /relay add weechat 8001

This will setup weechat to listen on port 8001 for connections.  Make sure your firewall is setup to allow access to this port.  You will probably want to set relay.network.password as well.  Communications with weechat are *not* encrypted at this point in time.

##Source Code
There are 3 basic parts to this, the android application, the java library which provides most of the functionality of connecting to the weechat relay server, and an example client showing the basic features of that library.

* weechat-android - The android application.
* weechat-relay - A library implementing the Weechat Relay Protocol
* weechat-relay-example - A simple example program that makes use of the weechat-relay library

You can build these projects by running 'ant' from this directory. If you wish to build them individually, you must build weechat-relay(the library) first.

To build the android application, you will need to 'cd weechat-android', then run 'android update project --path .' before running ant.

For more details about Weechat and the Relay Protocol:

* Weechat - http://www.weechat.org/
* Relay Protocol - http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html

##Screenshots
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/chat-channel.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/chat-channel.png" width="400px"></a>

<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/preferences.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/preferences.png" height="400px"></a>
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffers.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/buffers.png" height="400px"></a>
<a href="https://github.com/ubergeek42/weechat-android/raw/master/releases/notifications.png"><img src="https://github.com/ubergeek42/weechat-android/raw/master/releases/notifications.png" height="400px"></a>

In this last one, the "U: 2 H: 1" in purple refers to 2 unread messages, 1 highlight.  If it was only unread messages it is displayed in yellow.


## Changelog

#### v0.05-dev
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
