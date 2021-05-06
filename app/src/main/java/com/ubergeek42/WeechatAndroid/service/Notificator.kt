// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.AnyThread
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.relay.Hotlist.HotMessage
import com.ubergeek42.WeechatAndroid.relay.Hotlist.HotBuffer
import com.ubergeek42.WeechatAndroid.relay.Hotlist.NotifyReason
import com.ubergeek42.WeechatAndroid.relay.Hotlist.InlineReplyReceiver
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.utils.isAnyOf

import kotlin.apply
import kotlin.apply as apply2

private const val ID_MAIN = 42
private const val ID_HOT = 43
private const val CHANNEL_CONNECTION_STATUS = "connection status"
private const val CHANNEL_HOTLIST = "notification"
private const val CHANNEL_HOTLIST_ASYNC = "notification async"
private const val GROUP_KEY = "hot messages"
const val KEY_TEXT_REPLY = "key_text_reply"


// displayed in place of user name in private notifications, when we can get away with it
private val ZERO_WIDTH_SPACE: CharSequence = "\u200B"

// this text is somewhat displayed on android p when replying to notification
// represents “Me” in the “Me: my message” part of NotificationCompat.MessagingStyle
private var myself = Person.Builder().setName("Me").build()


private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


fun initializeNotificator(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    myself = Person.Builder().setName(context.getString(R.string.notifications__MessagingStyle__me)).build()

    // why not IMPORTANCE_MIN? it should not be used with startForeground. the docs say,
    // > If you do this, as of Android version O, the system will show a higher-priority
    // > notification about your app running in the background.
    // the user can manually hide the icon by setting the importance to low
    NotificationChannel(
        CHANNEL_CONNECTION_STATUS,
        applicationContext.getString(R.string.notifications__channel__connection_status),
        NotificationManager.IMPORTANCE_MIN
    ).apply {
        setShowBadge(false)
        manager.createNotificationChannel(this)
    }

    NotificationChannel(
        CHANNEL_HOTLIST,
        applicationContext.getString(R.string.notifications__channel__hotlist),
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        enableLights(true)
        manager.createNotificationChannel(this)
    }

    // channel for updating the notifications *silently*
    // it seems that you have to use IMPORTANCE_DEFAULT, else the notification light won't work
    NotificationChannel(
        CHANNEL_HOTLIST_ASYNC,
        applicationContext.getString(R.string.notifications__channel__hotlist_async),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        setSound(null, null)
        enableVibration(false)
        enableLights(true)
        manager.createNotificationChannel(this)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////


private val connectionStatusTapPendingIntent = PendingIntent.getActivity(
    applicationContext,
    0,
    Intent(applicationContext, WeechatActivity::class.java),
    PendingIntent.FLAG_CANCEL_CURRENT
)


private val disconnectActionPendingIntent = PendingIntent.getService(
    applicationContext,
    0,
    Intent(RelayService.ACTION_STOP, null, applicationContext, RelayService::class.java),
    0
)


@AnyThread fun showMainNotification(relay: RelayService, content: String) {
    val builder = NotificationCompat.Builder(
            applicationContext, CHANNEL_CONNECTION_STATUS)
        .setContentIntent(connectionStatusTapPendingIntent)
        .setSmallIcon(R.drawable.ic_notification_main)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setWhen(System.currentTimeMillis())
        .setPriority(Notification.PRIORITY_MIN)
        .setNotificationText(content)

    val textResource = if (relay.state.contains(RelayService.STATE.AUTHENTICATED))
            R.string.menu__connection_state__disconnect else
            R.string.menu__connection_state__stop_connecting

    builder.addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        applicationContext.getString(textResource),
        disconnectActionPendingIntent
    )

    val notification = builder.build()
    notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
    relay.startForeground(ID_MAIN, notification)
}


////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////


private val CAN_MAKE_BUNDLED_NOTIFICATIONS = Build.VERSION.SDK_INT >= 24
private val applicationResources = applicationContext.resources


// setting action in this way is not quite a proper way, but this ensures that all intents
// are treated as separate intents
fun makePendingIntentForBuffer(pointer: Long): PendingIntent {
    val intent = Intent(applicationContext, WeechatActivity::class.java).apply {
        putExtra(Constants.EXTRA_BUFFER_POINTER, pointer)
        action = Utils.pointerToString(pointer)
    }
    return PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}


fun makePendingIntentForDismissedNotificationForBuffer(pointer: Long): PendingIntent {
    val intent = Intent(applicationContext, NotificationDismissedReceiver::class.java).apply {
        action = Utils.pointerToString(pointer)
    }
    return PendingIntent.getBroadcast(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}


class HotNotification(
    private val connected: Boolean,
    private val totalHotCount: Int,
    private val hotBufferCount: Int,
    private val allMessages: List<HotMessage>,
    private val hotBuffer: HotBuffer,
    private val reason: NotifyReason,
    private val lastMessageTimestamp: Long,
) {
    // display a notification with a hot message. clicking on it will open the buffer & scroll up
    // to the hot line, if needed. mind that SOMETIMES hotCount will be larger than hotList, because
    // it's filled from hotlist data and hotList only contains lines that arrived in real time. so
    // we add (message not available) if there are NO lines to display and add "..." if there are
    // some lines to display, but not all
    fun show() {
        if (!P.notificationEnable) return

        // when redrawing notifications in order to remove the reply button, make sure we don't
        // add back notifications that were dismissed. synchronize `notifications.contains`
        if (reason == NotifyReason.REDRAW && !isNotificationDisplayedFor(hotBuffer.pointer)) return

        if (hotBuffer.hotCount == 0) {
            manager.cancel(Utils.pointerToString(hotBuffer.pointer), ID_HOT)
            val dismissResult = onNotificationDismissed(hotBuffer.pointer)
            if (dismissResult.isAnyOf(DismissResult.ALL_NOTIFICATIONS_REMOVED, DismissResult.NO_CHANGE))
                return
        }

        val shouldMakeNoise = reason == NotifyReason.HOT_SYNC

        if (!CAN_MAKE_BUNDLED_NOTIFICATIONS) {
            manager.notify(ID_HOT,
                makeSummaryNotification().apply { if (shouldMakeNoise) makeNoise() }.build())
        } else {
            manager.notify(ID_HOT,
                makeSummaryNotification().build())

            if (hotBuffer.hotCount > 0) {
                manager.notify(Utils.pointerToString(hotBuffer.pointer), ID_HOT,
                    makeBufferNotification().apply { if (shouldMakeNoise) makeNoise() }.build())

                onNotificationFired(hotBuffer.pointer)
            }
        }
    }

    private val notificationChannel = if (reason == NotifyReason.HOT_SYNC)
        CHANNEL_HOTLIST else CHANNEL_HOTLIST_ASYNC

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun makeSummaryNotification(): NotificationCompat.Builder {
        val nMessagesInNBuffers = applicationResources.getQuantityString(
            R.plurals.notifications__hot_summary__messages, totalHotCount, totalHotCount
        ) + applicationResources.getQuantityString(
            R.plurals.notifications__hot_summary__in_buffers, hotBufferCount, hotBufferCount
        )

        val builder = NotificationCompat.Builder(applicationContext, notificationChannel)
            .setContentIntent(makePendingIntentForBuffer(Constants.NOTIFICATION_EXTRA_BUFFER_ANY))
            .setSmallIcon(R.drawable.ic_notification_hot)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(lastMessageTimestamp)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setNotificationText(nMessagesInNBuffers)

        if (CAN_MAKE_BUNDLED_NOTIFICATIONS) {
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            builder.setSubText(nMessagesInNBuffers)
        } else {
            NotificationCompat.MessagingStyle(myself).apply2 {
                conversationTitle = nMessagesInNBuffers
                isGroupConversation = true // needed to display the title

                maybeAddMissingMessageLine(totalHotCount - allMessages.size, null)

                allMessages.forEach { message ->
                    addMessage(
                        message.message, message.timestamp,
                        message.getNickForFullList(), message.image
                    )
                }

                builder.setStyle(this@apply2)
            }
        }

        return builder
    }

    private fun makeBufferNotification(): NotificationCompat.Builder {
        val nNewMessagesInBuffer = applicationResources.getQuantityString(
            R.plurals.notifications__hot__text,
            hotBuffer.hotCount,
            hotBuffer.hotCount,
            hotBuffer.shortName
        )

        val builder = NotificationCompat.Builder(applicationContext, notificationChannel)
            .setContentIntent(makePendingIntentForBuffer(hotBuffer.pointer))
            .setSmallIcon(R.drawable.ic_notification_hot)
            .setDeleteIntent(makePendingIntentForDismissedNotificationForBuffer(hotBuffer.pointer))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(hotBuffer.lastMessageTimestamp)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setNotificationText(nNewMessagesInBuffer)

        // messages hold the latest messages, don't show the reply button if user can't see any
        if (connected && hotBuffer.messages.isNotEmpty()) {
            builder.addAction(makeActionForBufferPointer(Utils.pointerToString(hotBuffer.pointer)))
        }

        NotificationCompat.MessagingStyle(myself).apply2 {
            // this is ugly on android p, but i see no other way to show the number of messages
            conversationTitle = if (hotBuffer.hotCount < 2)
                    hotBuffer.shortName else "${hotBuffer.shortName} (${hotBuffer.hotCount})"

            // before pie, display private buffers as non-private
            isGroupConversation = !hotBuffer.isPrivate || Build.VERSION.SDK_INT < 28

            maybeAddMissingMessageLine(hotBuffer.hotCount - hotBuffer.messages.size, null)

            hotBuffer.messages.forEach { message ->
                addMessage(
                    message.message, message.timestamp,
                    message.getNickForBuffer(), message.image
                )
            }

            builder.setStyle(this@apply2)
        }

        return builder
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


// messaging style doesn't have a notion of text messages with images attached, so this method
// adds another message from the same person that is an image. the text part of this additional
// message is only visible when the image can't be displayed, namely, when this notification is
// a part of a notification group with a summary. in this state only the last message is
// visible, so it's safe enough to duplicate the text as well
// todo perhaps check if the image referenced by the url actually exists?
fun NotificationCompat.MessagingStyle.addMessage(
    message: CharSequence,
    timestamp: Long,
    nick: CharSequence,
    image: Uri?
) {
    val person = Person.Builder().setName(nick).build()

    addMessage(NotificationCompat.MessagingStyle.Message(message, timestamp, person))

    if (image != null) {
        addMessage(NotificationCompat.MessagingStyle.Message(message, timestamp, person)
                .setData("image/", image))
    }
}


// WARNING hotBuffer shouldn't be null on android p!
// we have to display *something* (not a space) in place of the user name on android p, since
// the big icon is generated from it. this is ugly but ¯\_(ツ)_/¯
// in case when we have any lines at all in a private buffer, set the nick to the nick
// of the first line we have to avoid awkward nick changes (current (missing) -> old -> current)
fun NotificationCompat.MessagingStyle.maybeAddMissingMessageLine(
    missingMessages: Int,
    hotBuffer: HotBuffer?
) {
    if (missingMessages == 0) return

    val nick = when {
        Build.VERSION.SDK_INT < 28 -> ZERO_WIDTH_SPACE
        hotBuffer != null && hotBuffer.isPrivate ->
            if (hotBuffer.messages.isEmpty()) hotBuffer.shortName else hotBuffer.messages[0].nick
        else ->
            applicationResources.getQuantityString(
                R.plurals.notifications__MessagingStyle__missing_users,
                if (missingMessages == 1) 1 else 2)
    }

    val message = if (missingMessages == 1) {
        applicationResources.getString(R.string.notifications__MessagingStyle__missing_messages_1)
    } else {
        applicationResources.getQuantityString(
            R.plurals.notifications__MessagingStyle__missing_messages,
            missingMessages, missingMessages
        )
    }

    addMessage(message, 0, nick, null)
}


fun NotificationCompat.Builder.makeNoise() {
    var flags = 0
    if (P.notificationLight) flags = flags or Notification.DEFAULT_LIGHTS
    if (P.notificationVibrate) flags = flags or Notification.DEFAULT_VIBRATE
    setDefaults(flags)
    priority = Notification.PRIORITY_HIGH
    if (!P.notificationSound.isNullOrBlank()) setSound(Uri.parse(P.notificationSound))
}


fun HotMessage.getNickForFullList(): CharSequence {
    return when {
        isAction && hotBuffer.isPrivate -> ZERO_WIDTH_SPACE
        !isAction && !hotBuffer.isPrivate -> "${hotBuffer.shortName}: $nick"
        else -> "${hotBuffer.shortName}:"
    }
}


// for android p, use the regular nick, as messages from the same user are displayed without
// repeating the name. on previous versions of android, use a fake name; the real name (short
// buffer name, actually) will be displayed as conversation title
// it is possible to have “nick (3)” as a nickname, but icon shade depends on the nickname, and
// you can't get away with making a Person's with different names but the same key
private fun HotMessage.getNickForBuffer(): CharSequence {
    return when {
        Build.VERSION.SDK_INT >= 28 -> nick
        hotBuffer.isPrivate || isAction -> ZERO_WIDTH_SPACE
        else -> nick
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


private fun makeActionForBufferPointer(strPointer: String): NotificationCompat.Action {
    val replyLabel = applicationResources.getString(R.string.notifications__RemoteInput__label)
    val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
        .setLabel(replyLabel)
        .build()

    val replyPendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        1,
        Intent(applicationContext, InlineReplyReceiver::class.java).apply { action = strPointer },
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Action.Builder(
            R.drawable.ic_toolbar_send, replyLabel, replyPendingIntent)
        .addRemoteInput(remoteInput)
        .build()
}


// don't display 2 lines of text on platforms that handle a single line fine
private fun NotificationCompat.Builder.setNotificationText(text: CharSequence): NotificationCompat.Builder {
    if (Build.VERSION.SDK_INT > 23) {
        setContentTitle(text)
    } else {
        setContentTitle(applicationContext.getString(R.string.etc__application_name))
        setContentText(text)
    }
    return this
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private enum class DismissResult {
    NO_CHANGE, ONE_NOTIFICATION_REMOVED, ALL_NOTIFICATIONS_REMOVED
}


private val displayedNotificationsLock = Any()
private val displayedNotifications = mutableSetOf<Long>()


@AnyThread private fun onNotificationFired(pointer: Long) {
    synchronized(displayedNotificationsLock) {
        displayedNotifications.add(pointer)
    }
}

@AnyThread private fun onNotificationDismissed(pointer: Long): DismissResult {
    synchronized(displayedNotificationsLock) {
        val removed = displayedNotifications.remove(pointer)

        return when {
            displayedNotifications.isEmpty() -> {
                manager.cancel(ID_HOT)
                DismissResult.ALL_NOTIFICATIONS_REMOVED
            }
            removed -> DismissResult.ONE_NOTIFICATION_REMOVED
            else -> DismissResult.NO_CHANGE
        }
    }
}


@AnyThread private fun isNotificationDisplayedFor(pointer: Long): Boolean {
    synchronized(displayedNotificationsLock) {
        return displayedNotifications.contains(pointer)
    }
}


class NotificationDismissedReceiver : BroadcastReceiver() {
    @MainThread override fun onReceive(context: Context, intent: Intent) {
        val strPointer = intent.action
        onNotificationDismissed(Utils.pointerFromString(strPointer))
    }
}
