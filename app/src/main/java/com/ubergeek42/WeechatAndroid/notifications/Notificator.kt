// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.notifications

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.BUBBLE_PREFERENCE_ALL
import android.app.NotificationManager.BUBBLE_PREFERENCE_SELECTED
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.BubbleActivity
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.dialogs.createScrollableDialog
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.as0x
import com.ubergeek42.WeechatAndroid.relay.from0xOrNull
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


// In dark mode, the icon color might be of the same hue but lighter than the original color.
// Note that even though the documentation says that setColor sets the background color of the icon,
// in some cases the foreground will be colored instead,
// particularly on stock Android, when coloring the small icon attached to the conversation icon.
val notificationIconBackgroundColor = ContextCompat.getColor(applicationContext, R.color.launcherIconKittyBackgroundColor)


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
        vibrationPattern = longArrayOf(50L, 50L, 50L, 50L)
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
    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
)


private val disconnectActionPendingIntent = PendingIntent.getService(
    applicationContext,
    0,
    Intent(RelayService.ACTION_STOP, null, applicationContext, RelayService::class.java),
    PendingIntent.FLAG_IMMUTABLE
)

@AnyThread fun showMainNotification(relay: RelayService, content: String) {
    val builder = NotificationCompat.Builder(
            applicationContext, CHANNEL_CONNECTION_STATUS)
        .setContentIntent(connectionStatusTapPendingIntent)
        .setSmallIcon(R.drawable.ic_notification_main)
        .setColor(notificationIconBackgroundColor)
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
private inline fun <reified T : Activity> makeActivityIntent(pointer: Long): PendingIntent {
    val intent = Intent(applicationContext, T::class.java).apply {
        putExtra(Constants.EXTRA_BUFFER_POINTER, pointer)
        action = pointer.as0x
    }
    return PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE) // Must be mutable!
}


private inline fun <reified T : BroadcastReceiver> makeBroadcastIntent(pointer: Long): PendingIntent {
    val intent = Intent(applicationContext, T::class.java).apply { action = pointer.as0x }
    return PendingIntent.getBroadcast(applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE) // Must be mutable!
}


private val summaryNotificationIntent = makeActivityIntent<WeechatActivity>(Constants.EXTRA_BUFFER_POINTER_ANY)
private val summaryNotificationDismissIntent = makeBroadcastIntent<NotificationDismissedReceiver>(0)


//////////////////////////////////////////////////////////////////////////////////// notify & cancel


private fun cancelOrSuppressUnwantedNotifications(hotlistBuffers: Collection<HotlistBuffer>) {
    if (Build.VERSION.SDK_INT >= 30) fixupBubblesThatShouldBeKept()

    val unwantedNotifications = displayedNotifications - hotlistBuffers.map { it.pointer }
    val bubbledNotificationsToCancel = bubblesThatShouldBeKept intersect unwantedNotifications
    val notificationsToCancel = unwantedNotifications - bubbledNotificationsToCancel
    val noNotificationsRemainAfterRemove =
            (displayedNotifications == notificationsToCancel) && bubblesThatShouldBeKept.isEmpty()
    val shouldCancelSummary = if (CAN_MAKE_BUNDLED_NOTIFICATIONS) {
                                  noNotificationsRemainAfterRemove
                              } else {
                                  hotlistBuffers.isEmpty()
                              }

    notificationsToCancel.forEach { pointer ->
        kitty.trace("canceling buffer notification for %s", pointer)
        manager.cancel(pointer.as0x, ID_HOT)
        displayedNotifications = displayedNotifications - pointer
    }

    // instead of canceling bubbling notification, suppress them
    // see https://developer.android.com/guide/topics/ui/bubbles#best_practices
    bubbledNotificationsToCancel.forEach { pointer ->
        getHotBuffer(pointer)?.let {
            kitty.trace("republishing suppressed notification for bubble %s", pointer)
            val notification = makeEmptyBufferNotification(it.fullName)
                    .addBubbleMetadata(it, suppressNotification = true)
                    .setMakeNoise(false)
                    .build()
            manager.notify(pointer.as0x, ID_HOT, notification)
            displayedNotifications = displayedNotifications - pointer
        }
    }

    if (shouldCancelSummary) {
        kitty.trace("canceling summary notification")
        manager.cancel(ID_HOT)
        summaryNotificationDisplayed = false
    }
}


private fun pushSummaryNotification(hotlistBuffers: Collection<HotlistBuffer>, makeNoise: Boolean) {
    kitty.trace("publishing summary notification")
    val summaryNotification = makeSummaryNotification(hotlistBuffers)
            .setMakeNoise(makeNoise)
            .build()
    manager.notify(ID_HOT, summaryNotification)
    summaryNotificationDisplayed = true
}


private fun pushBufferNotification(hotBuffer: HotlistBuffer, makeNoise: Boolean, addReplyAction: Boolean) {
    kitty.trace("publishing buffer notification for %s", hotBuffer.pointer)
    val bufferNotification = makeBufferNotification(hotBuffer, addReplyAction)
            .addBubbleMetadata(hotBuffer, suppressNotification = false)
            .setMakeNoise(makeNoise)
            .build()
    manager.notify(hotBuffer.pointer.as0x, ID_HOT, bufferNotification)
    displayedNotifications = displayedNotifications + hotBuffer.pointer
}


private fun pushSummaryAndBufferNotifications(hotlistBuffers: Collection<HotlistBuffer>,
                                              hotBuffer: HotlistBuffer, makeNoise: Boolean) {
    if (!CAN_MAKE_BUNDLED_NOTIFICATIONS) {
        pushSummaryNotification(hotlistBuffers, makeNoise)
    } else {
        pushSummaryNotification(hotlistBuffers, false)
        pushBufferNotification(hotBuffer, makeNoise, addReplyAction = true)
    }
}


//////////////////////////////////////////////////////////////////////////////////// user dismissals


private fun notificationHasBeenDismissedByUser(pointer: Long) =
        if (CAN_MAKE_BUNDLED_NOTIFICATIONS) {
            pointer !in displayedNotifications
        } else {
            !summaryNotificationDisplayed
        }


// when user dismisses the notification by swiping it away, and it is accompanied by a bubble,
// the system doesn't fire notification's delete intent. also, the notification remains
// in the list of active notification retrieved by manager.getActiveNotifications()
private fun notificationMightHaveBeenDismissedByUser(fullName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) willBubble(fullName) else false


private fun ifNotificationStillDisplayed(hotBuffer: HotlistBuffer, action: () -> Unit) {
    if (notificationHasBeenDismissedByUser(hotBuffer.pointer)) {
        kitty.trace("not updating notification for %s as it is not displaying", hotBuffer.pointer)
        return
    }

    if (notificationMightHaveBeenDismissedByUser(hotBuffer.fullName)) {
        kitty.trace("not updating notification for %s as it is bubbling " +
                    "and might have been dismissed by user", hotBuffer.fullName)
        return
    }

    action()
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


@Cat fun filterNotifications(hotlistBuffers: Collection<HotlistBuffer>) {
    if (!P.notificationEnable) return
    cancelOrSuppressUnwantedNotifications(hotlistBuffers)
    if (summaryNotificationDisplayed) pushSummaryNotification(hotlistBuffers, false)
}


@Cat fun updateHotNotification(hotBuffer: HotlistBuffer, hotlistBuffers: Collection<HotlistBuffer>) {
    if (!P.notificationEnable) return
    ifNotificationStillDisplayed(hotBuffer) {
        cancelOrSuppressUnwantedNotifications(hotlistBuffers)
        pushSummaryAndBufferNotifications(hotlistBuffers, hotBuffer, makeNoise = false)
    }
}


@Cat fun showHotNotification(hotlistBuffers: Collection<HotlistBuffer>, hotBuffer: HotlistBuffer) {
    if (!P.notificationEnable) return
    pushSummaryAndBufferNotifications(hotlistBuffers, hotBuffer, makeNoise = true)
}


@Cat fun showHotAsyncNotification(hotlistBuffers: Collection<HotlistBuffer>, hotBuffer: HotlistBuffer) {
    if (!P.notificationEnable) return
    pushSummaryAndBufferNotifications(hotlistBuffers, hotBuffer, makeNoise = false)
}


@Cat fun addOrRemoveActionForCurrentNotifications(addReplyAction: Boolean) = notificationHandler.post {
    hotlistBuffers.values.forEach { hotBuffer ->
        ifNotificationStillDisplayed(hotBuffer) {
            pushBufferNotification(hotBuffer, makeNoise = false, addReplyAction)
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


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
        .setColor(notificationIconBackgroundColor)
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
        .setContentIntent(makeActivityIntent<WeechatActivity>(hotBuffer.pointer))
        .setDeleteIntent(makeBroadcastIntent<NotificationDismissedReceiver>(hotBuffer.pointer))
        .setSmallIcon(R.drawable.ic_notification_hot)
        .setColor(notificationIconBackgroundColor)
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
        builder.addAction(makeActionForBufferPointer(hotBuffer.pointer))
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
            .setColor(notificationIconBackgroundColor)
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
            .Builder(makeActivityIntent<BubbleActivity>(hotBuffer.pointer), icon)
            .setDeleteIntent(makeBroadcastIntent<BubbleDismissedReceiver>(hotBuffer.pointer))
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
private fun NotificationCompat.MessagingStyle.addMessage(
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
private fun NotificationCompat.MessagingStyle.maybeAddMissingMessageLine(
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


private fun NotificationCompat.Builder.setMakeNoise(makeNoise: Boolean): NotificationCompat.Builder {
    setOnlyAlertOnce(!makeNoise)

    if (!makeNoise) setSilent(true)

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


private fun makeActionForBufferPointer(pointer: Long): NotificationCompat.Action {
    val replyLabel = applicationResources.getString(R.string.notifications__RemoteInput__label)
    val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
        .setLabel(replyLabel)
        .build()

    val replyPendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        1,
        Intent(applicationContext, InlineReplyReceiver::class.java).apply { action = pointer.as0x },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE  // Must be mutable!
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


fun notifyBubbleActivityCreated(pointer: Long) {
    bubblesThatShouldBeKept = bubblesThatShouldBeKept + pointer
    BufferList.findByPointer(pointer)?.addOpenKey("bubble-activity", true)
}


// user dragged the bubble icon to the (x) area on the bottom of the screen.
// this does NOT prevent further bubbles from appearing!
fun notifyBubbleDismissed(pointer: Long) {
    bubblesThatShouldBeKept = bubblesThatShouldBeKept - pointer
    BufferList.findByPointer(pointer)?.removeOpenKey("bubble-activity")
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
private fun fixupBubblesThatShouldBeKept() {
    bubblesThatShouldBeKept.forEach { pointer ->
        val fullName = getHotBuffer(pointer)?.fullName
        val willBubble = fullName != null && willBubble(fullName)
        if (!willBubble) {
            kitty.trace("bubble has become invalid: %s", fullName ?: pointer)
            notifyBubbleDismissed(pointer)
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


// we only look at summary notification on older devices
// that do not display bundled notifications
private var summaryNotificationDisplayed = false

private var displayedNotifications = setOf<Long>()


class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pointer = intent.action?.from0xOrNull ?: -1
        kitty.trace("notification dismissed: %s", pointer)

        if (pointer == 0L) {
            summaryNotificationDisplayed = false
        } else {
            displayedNotifications = displayedNotifications - pointer
        }
    }
}


private var bubblesThatShouldBeKept = setOf<Long>()


class BubbleDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pointer = intent.action?.from0xOrNull ?: -1
        kitty.trace("bubble dismissed: %s", pointer)
        notifyBubbleDismissed(pointer)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


class InlineReplyReceiver : BroadcastReceiver() {
    @MainThread override fun onReceive(context: Context, intent: Intent) {
        val pointer = intent.action?.from0xOrNull ?: -1
        val input = intent.getInlineReplyText()
        val buffer = BufferList.findByPointer(pointer)

        if (input.isNullOrEmpty() || buffer == null) {
            kitty.error("error while receiving remote input: pointer=%s, input=%s, buffer=%s",
                        pointer, input, buffer)
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


////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////// willBubble
////////////////////////////////////////////////////////////////////////////////////////////////////


// the following tables shows when bubbles are shown, as well as some problematic cases
// where it's impossible to tell, from the API, if a bubble is shown or not

// |----------------------------|-------------------|--------------------|--------------------|
// |                            | Bubble            | Don't bubble       |                    |
// |                            | this conversation | this conversation  | Default            |
// |                            | canBubble()==true | canBubble()==false | canBubble()==false |
// |                            | mAllowBubbles==1  | mAllowBubbles==0   | mAllowBubbles==-1  |
// |----------------------------|-------------------|--------------------|--------------------|
// | All conversations          |                   |                    |                    |
// | can bubble                 |       bubble      |         *          |       bubble       |
// | areBubblesAllowed()==true  |                   |                    |         *          |
// | BUBBLE_PREFERENCE_ALL      |                   |                    |                    |
// |----------------------------|-------------------|--------------------|--------------------|
// | Selected conversations     |                   |                    |                    |
// | can bubble                 |       bubble      |                    |                    |
// | areBubblesAllowed()==false |         †         |                    |                    |
// | BUBBLE_PREFERENCE_SELECTED |                   |                    |                    |
// |----------------------------|-------------------|--------------------|--------------------|
// | Nothing can bubble         |                   |                    |                    |
// | areBubblesAllowed()==false |         †         |                    |                    |
// | BUBBLE_PREFERENCE_NONE     |                   |                    |                    |
// |----------------------------|-------------------|--------------------|--------------------|

// see https://stackoverflow.com/questions/68226137/
// see https://issuetracker.google.com/issues/192590347


private enum class ConversationBubblingSetting {
    CanBubble,
    CanNotBubble,
    Default
}


private enum class PackageBubblingSetting {
    AllConversationsCanBubble,
    SelectedConversationsCanBubble,
    NothingCanBubble
}


// mAllowBubbles is a private field not accessible by API,
// nor directly nor via hidden method getAllowBubbles().
// see the table above for the values it can take
@RequiresApi(Build.VERSION_CODES.R)
private fun NotificationChannel.getBubblingSetting(): ConversationBubblingSetting {
    return when {
        canBubble() -> ConversationBubblingSetting.CanBubble
        "mAllowBubbles=0" in this.toString() -> ConversationBubblingSetting.CanNotBubble
        else -> ConversationBubblingSetting.Default
    }
}


@RequiresApi(Build.VERSION_CODES.R)
private fun getPackageBubblingSetting(): PackageBubblingSetting {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        when (manager.bubblePreference) {
            BUBBLE_PREFERENCE_ALL -> PackageBubblingSetting.AllConversationsCanBubble
            BUBBLE_PREFERENCE_SELECTED -> PackageBubblingSetting.SelectedConversationsCanBubble
            else -> PackageBubblingSetting.NothingCanBubble
        }
    } else {
        // Below S, we can't actually detect when the setting is set to NothingCanBubble,
        // so let's pretend that user never uses it.
        // It is only problematic if the user follows this very unlikely scenario:
        //   * Enables bubbles for some SPECIFIC notifications
        //   * Changes the setting from “Selected conversations can bubble” to “Nothing can bubble”
        @Suppress("DEPRECATION")
        when (manager.areBubblesAllowed()) {
            true -> PackageBubblingSetting.AllConversationsCanBubble
            false -> PackageBubblingSetting.SelectedConversationsCanBubble
        }
    }
}


@RequiresApi(Build.VERSION_CODES.R)
private fun willBubble(fullName: String): Boolean {
    val conversationChannel = manager.getNotificationChannel(CHANNEL_HOTLIST, fullName)

    val packageBubblingSetting = getPackageBubblingSetting()
    val conversationBubblingSetting = conversationChannel?.getBubblingSetting()
            ?: ConversationBubblingSetting.Default

    return when(packageBubblingSetting) {
        PackageBubblingSetting.AllConversationsCanBubble -> {
            when (conversationBubblingSetting) {
                ConversationBubblingSetting.CanNotBubble -> false
                else -> true
            }
        }

        PackageBubblingSetting.SelectedConversationsCanBubble -> {
            when (conversationBubblingSetting) {
                ConversationBubblingSetting.CanBubble -> true
                else -> false
            }
        }

        // We can only detect this on Android S+
        PackageBubblingSetting.NothingCanBubble -> false
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////// Permission
////////////////////////////////////////////////////////////////////////////////////////////////////

// We show the rationale for the notification permission before requesting it.
// If the user approves, or denies it, we proceed, and never ask again.
// If they dismiss the system permission dialog, we ask again.

// Regarding the behavior of `shouldShowRequestPermissionRationale` in practice:
//
// When the permission is requested and *denied* repeatedly, on API 34 we can observe:
//   * Before the first permission request, should-show-rationale is false;
//   * The first time permission is requested, permission dialog is shown,
//     and after should-show-rationale is true;
//   * The second time, permission dialog is shown, and after should-show-rationale is false;
//   * The third time, permission dialog is not shown, and after should-show-rationale is false.
//
// However, when the user dismisses the dialog without pressing any buttons,
// `granted` is false and also should-show-rationale is false.
// Dismissals also don't seem to count towards any limits.
//
// Also note that if the user approves and denies the permission *in app settings*,
// it will count as a single denied permission request, that is:
//   * Before the first permission request, should-show-rationale is true;
//   * The first time permission is requested, permission dialog is shown,
//     and after it is denied, should-show-rationale is false.
//     However, just like above, if the dialog is dismissed, should-show-rationale remains true.
//
// Therefore, to catch the *first* time user denies the permission *in the dialog*,
// we should check whether `granted` is false,
// and also that should-show-rationale flips in the process.
//
// I wonder if this covers all workflows... This is exhausting

private var oldShouldShowRequestPermissionRationale = false

// Recreated with activity, so must be largely stateless
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationPermissionChecker(private val activity: WeechatActivity) {
    private val shouldShowRequestPermissionRationale get() =
        activity.shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)

    private val requestLauncher = activity.registerForActivityResult(RequestPermission()) { granted ->
        if (!granted && shouldShowRequestPermissionRationale != oldShouldShowRequestPermissionRationale) {
            permissionWasDeniedOnce = true
        }
        activity.connect()
    }

    fun requestNotificationPermission() {
        oldShouldShowRequestPermissionRationale = shouldShowRequestPermissionRationale
        requestLauncher.launch(POST_NOTIFICATIONS)
    }
}

private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(Weechat.applicationContext) }

private var permissionWasDeniedOnce: Boolean
    get() = preferences.getBoolean("notificationPermissionWasDeniedOnce", false)
    set(value) { preferences.edit { putBoolean("notificationPermissionWasDeniedOnce", value) } }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationPermissionRationaleDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return requireContext().createScrollableDialog {
            setTitle(R.string.dialog__notification_permission__title)
            setText(R.string.dialog__notification_permission__text)
            setPositiveButton(R.string.dialog__notification_permission__positive_button) {
                (requireActivity() as WeechatActivity)
                    .notificationPermissionChecker!!
                    .requestNotificationPermission()

            }
            setNegativeButton(R.string.dialog__notification_permission__negative_button) {
                permissionWasDeniedOnce = true
                (requireActivity() as WeechatActivity).connect()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Activity.shouldRequestNotificationPermission() =
    checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED && !permissionWasDeniedOnce

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun FragmentActivity.showNotificationPermissionRationaleDialog() {
    NotificationPermissionRationaleDialogFragment()
        .show(supportFragmentManager, "notification-permission-dialog")
}
