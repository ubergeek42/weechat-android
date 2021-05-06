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
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.annotation.MainThread
import androidx.annotation.AnyThread
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.cats.Cat
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.relay.Hotlist.HotMessage
import com.ubergeek42.WeechatAndroid.relay.Hotlist.HotBuffer
import com.ubergeek42.WeechatAndroid.relay.Hotlist.NotifyReason
import com.ubergeek42.WeechatAndroid.relay.Hotlist.InlineReplyReceiver
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Utils
import java.lang.StringBuilder
import java.util.HashSet
import kotlin.jvm.Synchronized


private const val NOTIFICATION_MAIN_ID = 42
private const val NOTIFICATION_HOT_ID = 43
private const val NOTIFICATION_CHANNEL_CONNECTION_STATUS = "connection status"
private const val NOTIFICATION_CHANNEL_HOTLIST = "notification"
private const val NOTIFICATION_CHANNEL_HOTLIST_ASYNC = "notification async"
private const val GROUP_KEY = "hot messages"

// displayed in place of user name in private notifications, when we can get away with it
private val ZERO_WIDTH_SPACE: CharSequence = "\u200B"

// this text is somewhat displayed on android p when replying to notification
// represents “Me” in the “Me: my message” part of NotificationCompat.MessagingStyle
private var MYSELF = Person.Builder().setName("Me").build()


private var manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


fun initializeNotificator(c: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    // why not IMPORTANCE_MIN? it should not be used with startForeground. the docs say,
    // If you do this, as of Android version O, the system will show a higher-priority
    // notification about your app running in the background.
    // the user can manually hide the icon by setting the importance to low
    val statusNotificationChannel = NotificationChannel(
        NOTIFICATION_CHANNEL_CONNECTION_STATUS,
        applicationContext.getString(R.string.notifications__channel__connection_status),
        NotificationManager.IMPORTANCE_MIN
    )
    statusNotificationChannel.setShowBadge(false)
    manager.createNotificationChannel(statusNotificationChannel)

    val hotlistNotificationChannel = NotificationChannel(
        NOTIFICATION_CHANNEL_HOTLIST,
        applicationContext.getString(R.string.notifications__channel__hotlist),
        NotificationManager.IMPORTANCE_HIGH
    )
    hotlistNotificationChannel.enableLights(true)
    manager.createNotificationChannel(statusNotificationChannel)

    // channel for updating the notifications *silently*
    // it seems that you have to use IMPORTANCE_DEFAULT, else the notification light won't work
    val asyncHotlistNotificationChannel = NotificationChannel(
        NOTIFICATION_CHANNEL_HOTLIST_ASYNC,
        applicationContext.getString(R.string.notifications__channel__hotlist_async),
        NotificationManager.IMPORTANCE_DEFAULT
    )
    asyncHotlistNotificationChannel.setSound(null, null)
    asyncHotlistNotificationChannel.enableVibration(false)
    asyncHotlistNotificationChannel.enableLights(true)
    manager.createNotificationChannel(asyncHotlistNotificationChannel)

    MYSELF = Person.Builder().setName(c.getString(R.string.notifications__MessagingStyle__me))
        .build()
}


////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////


object Notificator {
    @JvmStatic @AnyThread @Cat fun showMain(relay: RelayService, content: String) {
        val contentIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, WeechatActivity::class.java), PendingIntent.FLAG_CANCEL_CURRENT
        )
        val authenticated = relay.state.contains(RelayService.STATE.AUTHENTICATED)
        val builder = NotificationCompat.Builder(
            applicationContext, NOTIFICATION_CHANNEL_CONNECTION_STATUS
        )
        builder.setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.ic_notification_main)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setWhen(System.currentTimeMillis()).priority = Notification.PRIORITY_MIN
        setNotificationTitleAndText(builder, content)
        if (P.notificationTicker) builder.setTicker(content)
        val disconnectText =
            applicationContext.getString(if (authenticated)
                    R.string.menu__connection_state__disconnect else
                    R.string.menu__connection_state__stop_connecting)
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel, disconnectText,
            PendingIntent.getService(
                applicationContext, 0,
                Intent(RelayService.ACTION_STOP, null, applicationContext, RelayService::class.java),
                0
            )
        )
        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
        relay.startForeground(NOTIFICATION_MAIN_ID, notification)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private const val BUFFER = 0
    private const val LINE = 1

    // display a notification with a hot message. clicking on it will open the buffer & scroll up
    // to the hot line, if needed. mind that SOMETIMES hotCount will be larger than hotList, because
    // it's filled from hotlist data and hotList only contains lines that arrived in real time. so
    // we add (message not available) if there are NO lines to display and add "..." if there are
    // some lines to display, but not all
    @JvmStatic @AnyThread @Cat fun showHot(
        connected: Boolean, totalHotCount: Int, hotBufferCount: Int,
        allMessages: List<HotMessage>, hotBuffer: HotBuffer,
        reason: NotifyReason, lastMessageTimestamp: Long
    ) {
        if (!P.notificationEnable) return
        val pointer = hotBuffer.pointer

        // when redrawing notifications in order to remove the reply button, make sure we don't
        // add back notifications that were dismissed. synchronize `notifications.contains`
        synchronized(Notificator::class.java) {
            if (reason == NotifyReason.REDRAW && !notifications.contains(pointer)) return
        }

        // https://developer.android.com/guide/topics/ui/notifiers/notifications.html#back-compat
        val canMakeBundledNotifications = Build.VERSION.SDK_INT >= 24
        val res = applicationContext.resources
        val hotCount = hotBuffer.hotCount
        val messages: List<HotMessage> = hotBuffer.messages
        val shortName = hotBuffer.shortName

        if (hotCount == 0) {
            // TODO this doesn't cancel notifications that have remote input and have ben replied to
            // TODO on android p. not sure what to do about this--find a workaround or leave as is?
            // TODO https://issuetracker.google.com/issues/112319501
            manager.cancel(Utils.pointerToString(pointer), NOTIFICATION_HOT_ID)
            val dismissResult = onNotificationDismissed(pointer)
            if (dismissResult == DismissResult.ALL_NOTIFICATIONS_REMOVED || dismissResult == DismissResult.NO_CHANGE) return
        }

        val syncHotMessage = reason == NotifyReason.HOT_SYNC
        val channel = if (syncHotMessage)
                NOTIFICATION_CHANNEL_HOTLIST else
                NOTIFICATION_CHANNEL_HOTLIST_ASYNC

        ////////////////////////////////////////////////////////////////////////////////////////////

        // (re)build the “parent” summary notification. in practice, it should never be visible on
        // Lollipop and later, except for the SubText part, which appears on the right of app name
        val nMessagesInNBuffers = res.getQuantityString(
            R.plurals.notifications__hot_summary__messages, totalHotCount, totalHotCount
        ) + res.getQuantityString(
            R.plurals.notifications__hot_summary__in_buffers, hotBufferCount, hotBufferCount
        )

        val summary = NotificationCompat.Builder(applicationContext, channel)
                .setContentIntent(getIntentFor(Constants.NOTIFICATION_EXTRA_BUFFER_ANY))
                .setSmallIcon(R.drawable.ic_notification_hot)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setWhen(lastMessageTimestamp)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)

        setNotificationTitleAndText(summary, nMessagesInNBuffers)

        if (canMakeBundledNotifications) {
            summary.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            summary.setSubText(nMessagesInNBuffers)
        } else {
            val style = NotificationCompat.MessagingStyle(MYSELF)
            style.conversationTitle = nMessagesInNBuffers
            style.isGroupConversation = true // needed to display the title
            addMissingMessageLine(totalHotCount - allMessages.size, res, style, null)
            for (message in allMessages) addMessage(style, message.message, message.timestamp,
                    getNickForFullList(message), message.image)
            summary.setStyle(style)
            if (syncHotMessage) makeNoise(summary, res, allMessages)
        }

        manager.notify(NOTIFICATION_HOT_ID, summary.build())

        if (hotCount == 0) return
        if (!canMakeBundledNotifications) return

        ////////////////////////////////////////////////////////////////////////////////////////////

        val newMessageInB = res.getQuantityString(
                R.plurals.notifications__hot__text, hotCount, hotCount, shortName)
        val builder = NotificationCompat.Builder(applicationContext, channel)
                .setContentIntent(getIntentFor(pointer))
                .setSmallIcon(R.drawable.ic_notification_hot)
                .setDeleteIntent(getDismissIntentFor(pointer))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setWhen(hotBuffer.lastMessageTimestamp)
                .setGroup(GROUP_KEY)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        setNotificationTitleAndText(builder, newMessageInB)

        // messages hold the latest messages, don't show the reply button if user can't see any
        if (connected && messages.size > 0) builder.addAction(
            getAction(applicationContext, Utils.pointerToString(pointer))
        )

        val style = NotificationCompat.MessagingStyle(MYSELF)

        // this is ugly on android p, but i see no other way to show the number of messages
        style.conversationTitle = if (hotCount < 2) shortName else "$shortName ($hotCount)"

        // before pie, display private buffers as non-private
        style.isGroupConversation = !hotBuffer.isPrivate || Build.VERSION.SDK_INT < 28
        addMissingMessageLine(hotCount - messages.size, res, style, hotBuffer)

        for (message in messages) addMessage(style, message.message, message.timestamp,
                getNickForBuffer(message), message.image)
        builder.setStyle(style)

        if (syncHotMessage) makeNoise(builder, res, messages)
        manager.notify(Utils.pointerToString(pointer), NOTIFICATION_HOT_ID, builder.build())
        onNotificationFired(pointer)
    }

    // setting action in this way is not quite a proper way, but this ensures that all intents
    // are treated as separate intents
    private fun getIntentFor(pointer: Long): PendingIntent {
        val intent = Intent(applicationContext, WeechatActivity::class.java).putExtra(
            Constants.EXTRA_BUFFER_POINTER, pointer
        )
        intent.action = Utils.pointerToString(pointer)
        return PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getDismissIntentFor(pointer: Long): PendingIntent {
        val intent = Intent(applicationContext, NotificationDismissedReceiver::class.java)
        intent.action = Utils.pointerToString(pointer)
        return PendingIntent.getBroadcast(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // WARNING hotBuffer shouldn't be null on android p!
    // we have to display *something* (not a space) in place of the user name on android p, since
    // the big icon is generated from it. this is ugly but ¯\_(ツ)_/¯
    // in case when we have any lines at all in a private buffer, set the nick to the nick
    // of the first line we have to avoid awkward nick changes (current (missing) -> old -> current)
    private fun addMissingMessageLine(
        missingMessages: Int,
        res: Resources,
        style: NotificationCompat.MessagingStyle,
        hotBuffer: HotBuffer?
    ) {
        if (missingMessages == 0) return

        var nick = ZERO_WIDTH_SPACE
        if (Build.VERSION.SDK_INT >= 28) nick =
            if (hotBuffer != null && hotBuffer.isPrivate) {
                if (hotBuffer.messages.isEmpty()) hotBuffer.shortName else hotBuffer.messages[0].nick
            } else res.getQuantityString(
                R.plurals.notifications__MessagingStyle__missing_users,
                if (missingMessages == 1) 1 else 2
            )
        val message =
            if (missingMessages == 1) {
                res.getString(R.string.notifications__MessagingStyle__missing_messages_1)
            } else res.getQuantityString(
                R.plurals.notifications__MessagingStyle__missing_messages,
                missingMessages, missingMessages
            )
        addMessage(style, message, 0, nick, null)
    }

    private fun makeNoise(
        builder: NotificationCompat.Builder,
        res: Resources,
        messages: List<HotMessage>
    ) {
        if (P.notificationTicker) builder.setTicker(
            if (messages.size == 0) res.getQuantityString(
                R.plurals.notifications__MessagingStyle__missing_messages,
                1
            ) else messages[messages.size - 1].forTicker()
        )
        builder.priority = Notification.PRIORITY_HIGH
        if (!TextUtils.isEmpty(P.notificationSound)) builder.setSound(Uri.parse(P.notificationSound))
        var flags = 0
        if (P.notificationLight) flags = flags or Notification.DEFAULT_LIGHTS
        if (P.notificationVibrate) flags = flags or Notification.DEFAULT_VIBRATE
        builder.setDefaults(flags)
    }

    const val KEY_TEXT_REPLY = "key_text_reply"

    private fun getAction(ctx: Context, strPointer: String): NotificationCompat.Action {
        val replyLabel = ctx.resources.getString(R.string.notifications__RemoteInput__label)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(replyLabel)
            .build()
        val intent = Intent(ctx, InlineReplyReceiver::class.java)
        intent.action = strPointer
        val replyPendingIntent = PendingIntent.getBroadcast(
            ctx,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_toolbar_send,
            replyLabel, replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // for android p, use the regular nick, as messages from the same user are displayed without
    // repeating the name. on previous versions of android, use a fake name; the real name (short
    // buffer name, actually) will be displayed as conversation title
    // it is possible to have “nick (3)” as a nickname, but icon shade depends on the nickname, and
    // you can't get away with making a Person's with different names but the same key

    private fun getNickForBuffer(message: HotMessage): CharSequence {
        if (Build.VERSION.SDK_INT >= 28) return message.nick // + " (" + hotCount + ")";
        return if (message.hotBuffer.isPrivate || message.isAction) ZERO_WIDTH_SPACE else message.nick
    }

    private fun getNickForFullList(message: HotMessage): CharSequence {
        if (message.isAction && message.hotBuffer.isPrivate) return ZERO_WIDTH_SPACE
        val text = StringBuilder(message.hotBuffer.shortName).append(":")
        if (!message.isAction && !message.hotBuffer.isPrivate) text.append(" ").append(message.nick)
        return text.toString()
    }

    // don't display 2 lines of text on platforms that handle a single line fine
    private fun setNotificationTitleAndText(
        builder: NotificationCompat.Builder,
        text: CharSequence
    ) {
        if (Build.VERSION.SDK_INT > 23) {
            builder.setContentTitle(text)
        } else {
            builder.setContentTitle(applicationContext!!.getString(R.string.etc__application_name) + " " + BuildConfig.VERSION_NAME)
                .setContentText(text)
        }
    }

    // messaging style doesn't have a notion of text messages with images attached, so this method
    // adds another message from the same person that is an image. the text part of this additional
    // message is only visible when the image can't be displayed, namely, when this notification is
    // a part of a notification group with a summary. in this state only the last message is
    // visible, so it's safe enough to duplicate the text as well
    // todo perhaps check if the image referenced by the url actually exists?
    private fun addMessage(
        style: NotificationCompat.MessagingStyle,
        message: CharSequence,
        timestamp: Long,
        nick: CharSequence,
        image: Uri?
    ) {
        val p = Person.Builder().setName(nick).build()
        style.addMessage(NotificationCompat.MessagingStyle.Message(message, timestamp, p))
        if (image != null) style.addMessage(
            NotificationCompat.MessagingStyle.Message(
                message,
                timestamp,
                p
            ).setData("image/", image)
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private val notifications: MutableSet<Long> = HashSet()
    @AnyThread @Synchronized private fun onNotificationFired(pointer: Long) {
        notifications.add(pointer)
    }

    @AnyThread @Synchronized private fun onNotificationDismissed(pointer: Long): DismissResult {
        val removed = notifications.remove(pointer)
        if (notifications.isEmpty()) {
            manager.cancel(NOTIFICATION_HOT_ID)
            return DismissResult.ALL_NOTIFICATIONS_REMOVED
        }
        return if (removed) DismissResult.ONE_NOTIFICATION_REMOVED else DismissResult.NO_CHANGE
    }

    private enum class DismissResult {
        NO_CHANGE, ONE_NOTIFICATION_REMOVED, ALL_NOTIFICATIONS_REMOVED
    }

    class NotificationDismissedReceiver : BroadcastReceiver() {
        @MainThread override fun onReceive(context: Context, intent: Intent) {
            val strPointer = intent.action
            onNotificationDismissed(Utils.pointerFromString(strPointer))
        }
    }
}