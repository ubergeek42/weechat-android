// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.notifications

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import com.ubergeek42.WeechatAndroid.BubbleActivity
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

import kotlin.apply
import kotlin.apply as apply2


@Root private val kitty = Kitty.make("Notificator") as Kitty


private const val ID_MAIN = 42
private const val ID_HOT = 43
private const val CHANNEL_CONNECTION_STATUS = "connection status"
private const val CHANNEL_HOTLIST = "notification"
private const val GROUP_KEY = "hot messages"
private const val KEY_TEXT_REPLY = "key_text_reply"


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
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


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


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private val CAN_MAKE_BUNDLED_NOTIFICATIONS = Build.VERSION.SDK_INT >= 24
private val applicationResources = applicationContext.resources


// setting action in this way is not quite a proper way, but this ensures that all intents
// are treated as separate intents. on API 29, `identifier` can be used instead
private inline fun <reified T : Activity> makeActivityIntent(fullName: String): PendingIntent {
    val intent = Intent(applicationContext, T::class.java).apply {
        putExtra(Constants.EXTRA_BUFFER_FULL_NAME, fullName)
        action = fullName
    }
    return PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}


private inline fun <reified T : BroadcastReceiver> makeBroadcastIntent(fullName: String): PendingIntent {
    val intent = Intent(applicationContext, T::class.java).apply { action = fullName }
    return PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}


private val summaryNotificationIntent = makeActivityIntent<WeechatActivity>(Constants.EXTRA_BUFFER_FULL_NAME_ANY)
private val summaryNotificationDismissIntent = makeBroadcastIntent<NotificationDismissedReceiver>("")


////////////////////////////////////////////////////////////////////////////////////////////////////


@Cat fun showHotNotification(hotBuffer: HotlistBuffer, allHotBuffers: Collection<HotlistBuffer>, makeNoise: Boolean) {
    if (!P.notificationEnable) return

    val publishingNewNotification = hotBuffer in allHotBuffers

    // on api < 24, if the user dismissed *the* notification,
    // make sure we don't add it back if the user only reads a hot buffer
    if (!CAN_MAKE_BUNDLED_NOTIFICATIONS && !summaryNotificationDisplayed
            && !publishingNewNotification) return

    //
    if (Build.VERSION.SDK_INT >= 30) fixupBubblesThatShouldBeKept()

    val unwantedNotifications = displayedNotifications - allHotBuffers.map { it.fullName }
    val bubbledNotificationsToCancel = bubblesThatShouldBeKept intersect unwantedNotifications
    val notificationsToCancel = unwantedNotifications - bubbledNotificationsToCancel
    val noNotificationsRemainAfterRemove =
            (displayedNotifications == notificationsToCancel) && bubblesThatShouldBeKept.isEmpty()

    notificationsToCancel.forEach { fullName ->
        kitty.trace("canceling buffer notification for %s", fullName)
        manager.cancel(fullName, ID_HOT)
        displayedNotifications = displayedNotifications - fullName
    }

    // instead of canceling bubbling notification, suppress them
    // see https://developer.android.com/guide/topics/ui/bubbles#best_practices
    bubbledNotificationsToCancel.forEach { fullName ->
        getHotBuffer(fullName)?.let {
            kitty.trace("republishing suppressed notification for bubble %s", fullName)
            val notification = makeEmptyBufferNotification(fullName)
                    .addBubbleMetadata(it, suppressNotification = true)
                    .setMakeNoise(false)
                    .build()
            manager.notify(fullName, ID_HOT, notification)
            displayedNotifications = displayedNotifications - fullName
        }
    }

    // make sure to not add back the summary if the user manually dismissed some
    // (which does not clear them from hotlist) and we have removed the remaining ones above
    val shouldCancelSummaryAndReturn = if (CAN_MAKE_BUNDLED_NOTIFICATIONS) {
        noNotificationsRemainAfterRemove && !publishingNewNotification
    } else {
        allHotBuffers.isEmpty()
    }

    if (shouldCancelSummaryAndReturn) {
        kitty.trace("canceling summary notification")
        manager.cancel(ID_HOT)
        return
    }

    if (!CAN_MAKE_BUNDLED_NOTIFICATIONS) {
        val summary = makeSummaryNotification(allHotBuffers).setMakeNoise(makeNoise).build()
        manager.notify(ID_HOT, summary)
        summaryNotificationDisplayed = true
    } else {
        kitty.trace("publishing summary notification")
        val summary = makeSummaryNotification(allHotBuffers).setMakeNoise(false).build()
        manager.notify(ID_HOT, summary)

        if (publishingNewNotification) {
            kitty.trace("publishing buffer notification for %s", hotBuffer.fullName)
            val notification = makeBufferNotification(hotBuffer, true)
                    .addBubbleMetadata(hotBuffer, false)
                    .setMakeNoise(makeNoise)
                    .build()
            manager.notify(hotBuffer.fullName, ID_HOT, notification)
            displayedNotifications = displayedNotifications + hotBuffer.fullName
        }
    }
}


fun addOrRemoveActionForCurrentNotifications(addReplyAction: Boolean) {
    notificationHandler.post {
        hotlistBuffers.values
                .filter { displayedNotifications.contains(it.fullName) }
                .forEach { hotBuffer ->
            kitty.trace("%s action for buffer notification %s", if (addReplyAction) "adding" else "removing", hotBuffer.fullName)
            val notification = makeBufferNotification(hotBuffer, addReplyAction)
                    .addBubbleMetadata(hotBuffer, suppressNotification = false)
                    .setMakeNoise(false)
                    .build()
            manager.notify(hotBuffer.fullName, ID_HOT, notification)
        }
    }
}


private fun makeSummaryNotification(hotBuffers: Collection<HotlistBuffer>): NotificationCompat.Builder {
    val hotBufferCount = hotBuffers.size
    val totalHotCount = hotBuffers.sumOf { it.hotCount }
    val lastMessageTimestamp = if (hotBuffers.isEmpty()) 0 else hotBuffers.maxOf { it.lastMessageTimestamp }

    val nMessagesInNBuffers = applicationResources.getQuantityString(
        R.plurals.notifications__hot_summary__messages, totalHotCount, totalHotCount
    ) + applicationResources.getQuantityString(
        R.plurals.notifications__hot_summary__in_buffers, hotBufferCount, hotBufferCount
    )

    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_HOTLIST)
        .setContentIntent(summaryNotificationIntent)
        .setSmallIcon(R.drawable.ic_notification_hot)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setGroup(GROUP_KEY)
        .setGroupSummary(true)

        .setWhen(lastMessageTimestamp)
        .setNotificationText(nMessagesInNBuffers)

    if (CAN_MAKE_BUNDLED_NOTIFICATIONS) {
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        builder.setSubText(nMessagesInNBuffers)
    } else {
        builder.setDeleteIntent(summaryNotificationDismissIntent)

        val allMessages = hotBuffers.flatMap { it.messages }.sortedBy { it.timestamp }

        NotificationCompat.MessagingStyle(myself).apply2 {
            conversationTitle = nMessagesInNBuffers
            isGroupConversation = true // needed to display the title

            maybeAddMissingMessageLine(totalHotCount - allMessages.size, null, null)

            allMessages.forEach { message ->
                val nick = message.hotBuffer.run {
                    if (isPrivate) message.nick else "${shortName}: ${message.nick}"
                }

                addMessage(
                    Person.Builder().setName(nick).build(),
                    message.message, message.timestamp,
                    message.image
                )
            }

            builder.setStyle(this@apply2)
        }
    }

    return builder
}


private fun makeBufferNotification(hotBuffer: HotlistBuffer, addReplyAction: Boolean): NotificationCompat.Builder {
    val nNewMessagesInBuffer = applicationResources.getQuantityString(
        R.plurals.notifications__hot__text,
        hotBuffer.hotCount,
        hotBuffer.hotCount,
        hotBuffer.shortName
    )

    shortcuts.ensureShortcutExists(hotBuffer.fullName)

    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_HOTLIST)
        .setContentIntent(makeActivityIntent<WeechatActivity>(hotBuffer.fullName))
        .setDeleteIntent(makeBroadcastIntent<NotificationDismissedReceiver>(hotBuffer.fullName))
        .setSmallIcon(R.drawable.ic_notification_hot)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setGroup(GROUP_KEY)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setShortcutId(hotBuffer.fullName)
        .setLocusId(LocusIdCompat(hotBuffer.fullName))

        .setWhen(hotBuffer.lastMessageTimestamp)
        .setNotificationText(nNewMessagesInBuffer)

    if (Build.VERSION.SDK_INT in 28..29 && !hotBuffer.isPrivate) {
        builder.setLargeIcon(obtainLegacyRoundIconBitmap(hotBuffer.shortName, hotBuffer.fullName))
    }

    // messages hold the latest messages, don't show the reply button if user can't see any
    if (addReplyAction && hotBuffer.messages.isNotEmpty()) {
        builder.addAction(makeActionForBufferPointer(hotBuffer.fullName))
    }

    NotificationCompat.MessagingStyle(myself).apply2 {
        conversationTitle = hotBuffer.shortName
        isGroupConversation = !hotBuffer.isPrivate

        val staticPerson = if (hotBuffer.isPrivate) getPersonByPrivateBuffer(hotBuffer) else null

        maybeAddMissingMessageLine(hotBuffer.hotCount - hotBuffer.messages.size, hotBuffer, staticPerson)

        hotBuffer.messages.forEach { message ->
            val person = if (staticPerson != null) {
                staticPerson
            } else {
                val nick = message.nick.toString()
                getPerson(key = nick, colorKey = nick, nick = nick, missing = false)
            }

            addMessage(person, message.message, message.timestamp, message.image)
        }

        builder.setStyle(this@apply2)
    }

    return builder
}


// same as above, but lacking content, for publishing suppressed notifications with bubble metadata
private fun makeEmptyBufferNotification(fullName: String): NotificationCompat.Builder {
    shortcuts.ensureShortcutExists(fullName)

    return NotificationCompat.Builder(applicationContext, CHANNEL_HOTLIST)
            .setSmallIcon(R.drawable.ic_notification_hot)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setShortcutId(fullName)
            .setLocusId(LocusIdCompat(fullName))
            .setStyle(NotificationCompat.MessagingStyle(myself))
}


private fun NotificationCompat.Builder.addBubbleMetadata(
    hotBuffer: HotlistBuffer,
    suppressNotification: Boolean
) = this.apply {
    if (Build.VERSION.SDK_INT >= 30) {
        val icon = obtainAdaptiveIcon(hotBuffer.shortName, hotBuffer.fullName, allowUriIcons = true)
        bubbleMetadata = NotificationCompat.BubbleMetadata
            .Builder(makeActivityIntent<BubbleActivity>(hotBuffer.fullName), icon)
            .setDeleteIntent(makeBroadcastIntent<BubbleDismissedReceiver>(hotBuffer.fullName))
            .setDesiredHeight(600 /* dp */)
            .setSuppressNotification(suppressNotification)
            .build()
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
    person: Person,
    message: CharSequence,
    timestamp: Long,
    image: Uri?,
) {
    addMessage(NotificationCompat.MessagingStyle.Message(message, timestamp, person))

    if (image != null) {
        image.grantReadPermissionToSystem()
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
    hotBuffer: HotlistBuffer?,
    staticPerson: Person?,
) {
    if (missingMessages == 0) return

    val person = if (staticPerson != null) {
        staticPerson
    } else {
        val nick = applicationResources.getQuantityString(
            R.plurals.notifications__MessagingStyle__missing_users,
            if (missingMessages == 1) 1 else 2)
        if (hotBuffer == null) {
            Person.Builder().setName(nick).build()
        } else {
            getPerson(key = hotBuffer.fullName, colorKey = hotBuffer.fullName,
                      nick = nick, missing = true)
        }
    }

    val message = if (missingMessages == 1) {
        applicationResources.getString(R.string.notifications__MessagingStyle__missing_messages_1)
    } else {
        applicationResources.getQuantityString(
            R.plurals.notifications__MessagingStyle__missing_messages,
            missingMessages, missingMessages
        )
    }

    addMessage(person, message, 0, null)
}


fun NotificationCompat.Builder.setMakeNoise(makeNoise: Boolean): NotificationCompat.Builder {
    setOnlyAlertOnce(!makeNoise)

    if (makeNoise && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        var flags = 0
        if (P.notificationLight) flags = flags or Notification.DEFAULT_LIGHTS
        if (P.notificationVibrate) flags = flags or Notification.DEFAULT_VIBRATE
        setDefaults(flags)

        P.notificationSound?.let{
            setSound(Uri.parse(it))
        }

        priority = Notification.PRIORITY_HIGH
    }

    return this
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


fun notifyBubbleActivityCreated(fullName: String) {
    bubblesThatShouldBeKept = bubblesThatShouldBeKept + fullName
    BufferList.findByFullName(fullName)?.addOpenKey("bubble-activity", true)
}


// user dragged the bubble icon to the (x) area on the bottom of the screen.
// this does NOT prevent further bubbles from appearing!
fun notifyBubbleDismissed(fullName: String) {
    bubblesThatShouldBeKept = bubblesThatShouldBeKept - fullName
    BufferList.findByFullName(fullName)?.removeOpenKey("bubble-activity")
}


// user can opt out of bubbles for specific conversations, e.g. by pressing ⇱ notification button.
// if a bubble is displayed, this removes it, BUT dismiss listener is not called.
// so let's verify that the bubbles we are trying to keep are valid
//
// this prevents the following scenario:
//   * get a highlight in a buffer, expand bubble ⇲, close it
//   * get another highlight, but this time remove bubble without opening by pressing ⇱
//   * visit buffer in main app. the app tries to publish a suppressed empty notification,
//     but as the bubble is disabled it doesn't get suppressed
@RequiresApi(Build.VERSION_CODES.R)
fun fixupBubblesThatShouldBeKept() {
    fun canBubble(fullName: String) = manager.getNotificationChannel(CHANNEL_HOTLIST, fullName)
            ?.canBubble() == true
    bubblesThatShouldBeKept.forEach { fullName ->
        if (!canBubble(fullName)) {
            kitty.trace("bubble has become invalid: %s", fullName)
            notifyBubbleDismissed(fullName)
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////


// we only look at summary notification on older devices
// that do not display bundled notifications
private var summaryNotificationDisplayed = false

private var displayedNotifications = setOf<String>()


class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fullName = intent.action ?: ""
        kitty.trace("notification dismissed: %s", fullName)

        if (fullName == "") {
            summaryNotificationDisplayed = false
        } else {
            displayedNotifications = displayedNotifications - fullName
        }
    }
}


var bubblesThatShouldBeKept = setOf<String>()


class BubbleDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fullName = intent.action ?: ""
        kitty.trace("bubble dismissed: %s", fullName)
        notifyBubbleDismissed(fullName)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


class InlineReplyReceiver : BroadcastReceiver() {
    @MainThread override fun onReceive(context: Context, intent: Intent) {
        val fullName = intent.action ?: ""
        val input = intent.getInlineReplyText()
        val buffer = BufferList.findByFullName(fullName)

        if (input.isNullOrEmpty() || buffer == null) {
            kitty.error("error while receiving remote input: fullName=%s, input=%s, buffer=%s",
                        fullName, input, buffer)
            Toaster.ErrorToast.show("Error while receiving remote input")
        } else {
            Events.SendMessageEvent.fireInput(buffer, input.toString())
            buffer.flagResetHotMessagesOnNewOwnLine = true
        }
    }
}


private fun Intent.getInlineReplyText(): CharSequence? {
    val remoteInput = RemoteInput.getResultsFromIntent(this)
    return remoteInput?.getCharSequence(KEY_TEXT_REPLY)
}


val notificationHandler = Handler(HandlerThread("notifications").apply { start() }.looper)
