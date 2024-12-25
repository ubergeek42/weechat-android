@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "MemberVisibilityCanBePrivate")

package com.ubergeek42.WeechatAndroid.relay

import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.weechat.relay.connection.Handshake
import com.ubergeek42.weechat.relay.connection.Handshake.Companion.weechatVersion
import com.ubergeek42.weechat.relay.connection.find
import com.ubergeek42.weechat.relay.protocol.Hashtable
import com.ubergeek42.weechat.relay.protocol.HdataEntry
import kotlin.jvm.JvmInline


////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////// buffer
////////////////////////////////////////////////////////////////////////////////////////////////////


// https://weechat.org/files/doc/stable/weechat_plugin_api.en.html#_buffer_set
@Suppress("unused")
enum class Notify(val value: Int) {
    NeverAddToHotlist(0),
    AddHighlightsOnly(1),
    AddHighlightsAndMessages(2),
    AddAllMessages(3);

    companion object {
        val default = AddAllMessages
    }
}


@JvmInline value class BufferSpec(val entry: HdataEntry) {
    inline val pointer: Long get() = entry.pointerLong
    inline val number: Int get() = entry.getInt("number")
    inline val fullName: String get() = entry.getString("full_name")
    inline val shortName: String? get() = entry.getStringOrNull("short_name")
    inline val title: String? get() = entry.getStringOrNull("title")
    inline val notify: Notify? get() = Notify::value.find(entry.getIntOrNull("notify"))
    inline val type get() = Type.fromLocalVariables(entry.getHashtable("local_variables"))
    inline val hidden get() = entry.getIntOrNull("hidden") == 1

    // todo get rid of openWhileRunning
    fun toBuffer(openWhileRunning: Boolean) = Buffer(pointer).apply {
        update(silently = true) {
            number = this@BufferSpec.number
            fullName = this@BufferSpec.fullName
            shortName = this@BufferSpec.shortName
            title = this@BufferSpec.title
            notify = this@BufferSpec.notify
            hidden = this@BufferSpec.hidden
            type = this@BufferSpec.type
        }

        if (P.isBufferOpen(pointer)) addOpenKey("main-activity", syncHotlistOnOpen = false)

        // saved data would be meaningless for a newly opened buffer
        if (!openWhileRunning) P.restoreLastReadLine(this)

        // when a buffer is open while the application is already connected, such as when someone
        // pms us, we don't have lastReadLine yet and so the subsequent hotlist update will trigger
        // a full update. in order to avoid that, if we are syncing, let's pretend that a hotlist
        // update has happened at this point so that the next update is “synced”
        if (openWhileRunning && !P.optimizeTraffic) hotlistUpdatesWhileSyncing++

    }

    companion object {
        const val listBuffersRequest = "(listbuffers) hdata buffer:gui_buffers(*) " +
                "number,full_name,short_name,type,title,nicklist,local_variables,notify,hidden"

        const val renumberRequest = "(renumber) hdata buffer:gui_buffers(*) number"
    }

    // HardHidden is a special thing. to hide buffer from relay only,
    // do /buffer set localvar_set_relay hard-hide
    // todo make this not a type?
    enum class Type(val colorRes: Int, val hotColorRes: Int) {
        Private(R.color.bufferListPrivate, R.color.bufferListPrivateHot),
        Channel(R.color.bufferListChannel, R.color.bufferListChannelHot),
        Other(R.color.bufferListOther, R.color.bufferListOtherHot),
        HardHidden(R.color.bufferListOther, R.color.bufferListOtherHot);

        companion object {
            fun fromLocalVariables(localVariables: Hashtable): Type {
                val relay = localVariables["relay"]

                return if (relay != null && relay.asString().split(",").contains("hard-hide")) {
                    HardHidden
                } else {
                    when(localVariables["type"]?.asString()) {
                        null -> Other
                        "private" -> Private
                        "channel" -> Channel
                        else -> Other
                    }
                }
            }
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////// hotlist
////////////////////////////////////////////////////////////////////////////////////////////////////


@JvmInline value class LastLinesSpec(val entry: HdataEntry) {
    inline val bufferPointer: Long get() = entry.getPointerLong("buffer")
    inline val linePointer: Long get() = if (weechatVersion >= 0x4040000) entry.getInt("id").toLong() else entry.pointerLong
    inline val visible: Boolean get() = entry.getChar("displayed") == 1.toChar()

    companion object {
        const val request = "(last_lines) hdata " +
                "buffer:gui_buffers(*)/own_lines/last_line(-25)/data id,buffer,displayed"
    }
}


@JvmInline value class LastReadLineSpec(val entry: HdataEntry) {
    inline val bufferPointer: Long get() = entry.getPointerLong("buffer")
    inline val linePointer: Long get() = if (weechatVersion >= 0x4040000) entry.getInt("id").toLong() else entry.pointerLong

    companion object {
        const val request = "(last_read_lines) hdata " +
                "buffer:gui_buffers(*)/own_lines/last_read_line/data id,buffer"
    }
}


class HotlistSpec(entry: HdataEntry) {
    val unreads: Int        // chat messages & private messages
    val highlights: Int     // highlights
    val bufferPointer = entry.getPointerLong("buffer")

    init {
        val count = entry.getArray("count")
        unreads = count[1].asInt() + count[2].asInt()
        highlights = count.get(3).asInt()
    }

    companion object {
        const val request = "(hotlist) hdata hotlist:gui_hotlist(*) buffer,count"
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////// line
////////////////////////////////////////////////////////////////////////////////////////////////////


@JvmInline value class LineSpec(val entry: HdataEntry) {
    inline val bufferPointer: Long get() = entry.getPointerLong("buffer")

    inline val pointer: Long get() = if (weechatVersion >= 0x4040000) entry.getInt("id").toLong() else entry.pointerLong
    inline val timestamp: Long get() = entry.getItem("date").asTime().time
    inline val prefix: String? get() = entry.getStringOrNull("prefix")
    inline val message: String? get() = entry.getStringOrNull("message")

    inline val visible: Boolean get() = entry.getChar("displayed") == 1.toChar()
    inline val highlight: Boolean get() = entry.getChar("highlight") == 1.toChar()

    inline val notifyLevel: NotifyLevel? get() = NotifyLevel.fromByte(
            entry.getByteOrNull("notify_level"))

    inline val tags: Array<String>? get() = entry.getStringArrayOrNull("tags_array")

    companion object {
        fun makeLastLinesRequest(id: String, pointer: Long, numberOfLines: Int) =
                "($id) hdata buffer:${pointer.as0x}/own_lines/last_line(-$numberOfLines)/data " +
                "id,date,displayed,prefix,message,highlight,notify,tags_array"
    }

    fun toLine(): Line {
        val tags = this.tags
        val highlight = this.highlight
        val notifyLevelByte = this.notifyLevel

        var nick: String? = null
        var type = Type.Other
        var displayAs = DisplayAs.Unspecified
        var notifyLevel = NotifyLevel.default

        if (tags == null) {
            // there are no tags, it's probably an old version of weechat, so we err
            // on the safe side and treat it as from human
            type = Type.IncomingMessage
        } else {
            var log1 = false
            var selfMsg = false
            var ircAction = false
            var ircPrivmsg = false
            var notifyNone = false

            for (tag in tags) {
                when (tag) {
                    "log1" -> log1 = true
                    "self_msg" -> selfMsg = true
                    "irc_privmsg" -> ircPrivmsg = true
                    "irc_action" -> ircAction = true
                    "notify_none" -> { notifyLevel = NotifyLevel.Disabled; notifyNone = true }
                    "notify_message" -> notifyLevel = NotifyLevel.Message
                    "notify_private" -> notifyLevel = NotifyLevel.Private
                    "notify_highlight" -> notifyLevel = NotifyLevel.Highlight
                    else -> if (tag.startsWith("nick_")) nick = tag.substring(5)
                }
            }

            // notify_level bit supersedes tags
            if (notifyLevelByte != null) {
                notifyLevel = notifyLevelByte
            }

            // starting with roughly 3.0 (2b16036), if a line has the tag notify_none, or if
            // notify_level is -1, it is not added to the hotlist nor it beeps—even if highlighted.
            val notifyNoneForced = Handshake.weechatVersion >= 0x3000000 &&
                    (notifyLevel == NotifyLevel.Disabled || notifyNone)

            if (notifyNoneForced) {
                notifyLevel = NotifyLevel.Disabled
            } else if (highlight) {
                // highlights from irc will have the level message or private upon inspection. since
                // we are thinking of notify as the level with which the message was added to
                // hotlist, we have to “fix” this by looking at the highlight bit.
                notifyLevel = NotifyLevel.Highlight
            }

            // log1 should be reliable enough method of telling if a line is a message from user,
            // our own or someone else's. see `/help logger`: log levels 2+ are nick changes, etc.
            // ignoring `prefix_nick_...`, `nick_...` and `host_...` tags:
            //   * join: `irc_join`, `log4`
            //   * user message:  `irc_privmsg`, `notify_message`, `log1`
            //   * own message:   `irc_privmsg`, `notify_none`, `self_msg`, `no_highlight`, `log1`
            // note: some messages such as those produced by `/help` itself won't have tags at all.
            val isMessageFromSelfOrUser = log1 || ircPrivmsg || ircAction

            if (tags.isNotEmpty() && isMessageFromSelfOrUser) {
                val isMessageFromSelf = selfMsg ||
                        Handshake.weechatVersion < 0x1070000 && notifyLevel == NotifyLevel.Disabled
                type = if (isMessageFromSelf) Type.OutgoingMessage else Type.IncomingMessage
            }

            if (ircAction) {
                displayAs = DisplayAs.Action
            } else if (ircPrivmsg) {
                displayAs = DisplayAs.Say
            }
        }

        return Line(pointer, type,
                timestamp, prefix ?: "", message ?: "",
                nick,
                visible, highlight,
                displayAs, notifyLevel)
    }


    enum class Type {
        Other,
        IncomingMessage,
        OutgoingMessage,
    }

    enum class DisplayAs {
        Unspecified,    // not any of:
        Say,            // can be written as <nick> message
        Action,         // can be written as * nick message
    }

    // notify levels, as specified by the `notify_xxx` tags and the `notify_level` bit, is the best
    // thing that we can use to determine if we should issue notifications. the algorithm is a tad
    // complicated, however.

    // * get notify level from the `notify_level` bit. it's better to use this as “its value is also
    //   affected by the max hotlist level you can set for some nicks (so not only tags in line)”
    //   * if not present, get notify level from tags (we are using the last tag)
    //     * if no `notify_*` tags are present, use low. see footnote at
    //       https://weechat.org/files/doc/devel/weechat_user.en.html#lines_format
    // * if weechat >= 3.0:
    //   * if either tag `notify_none` is present, or `notify_level` is none, make sure that our
    //     notify level is none. this can be not so in some weird situations
    // * if notify level wasn't coerced to none in the previous step, and if highlight bit is set,
    //   set notify level to highlight. highlights coming from irc can have notify levels message or
    //   private. see discussion at https://github.com/weechat/weechat/issues/1529

    // fun commands:
    //   /wait 1 /print -tags notify_none,notify_highlight foo\t bar
    //   /wait 1 /print -tags notify_none,irc_privmsg foo\t your_nick
    //     (irc_privmsg is needed for weechat to highlight your_nick)
    //   /buffer set hotlist_max_level_nicks_add someones_nick:0
    //     (see https://blog.weechat.org/post/2010/12/02/Max-hotlist-level-for-some-nicks)

    // our final `notify` field represents the “final product”. it is the level with which the line
    // was added to hotlist and if it's private or highlight we know we should issue a notification.

    // see table at https://weechat.org/files/doc/stable/weechat_plugin_api.en.html#_hook_line
    enum class NotifyLevel {
        Disabled,
        Low,
        Message,
        Private,
        Highlight;

        companion object {
            val default = Low

            fun fromByte(byte: Byte?): NotifyLevel? = when (byte) {
                (-1).toByte() -> Disabled
                0.toByte() -> Low
                1.toByte() -> Message
                2.toByte() -> Private
                3.toByte()-> Highlight
                else -> null
            }
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////// nick
////////////////////////////////////////////////////////////////////////////////////////////////////


internal const val ADD = '+'
internal const val REMOVE = '-'
internal const val UPDATE = '*'


@JvmInline value class NickSpec(val entry: HdataEntry) {
    inline val bufferPointer: Long get() = entry.getPointerLong(0)

    inline val pointer: Long get() = entry.pointerLong
    inline val prefix: String? get() = entry.getStringOrNull("prefix")
    inline val name: String get() = entry.getString("name")
    inline val color: String? get() = entry.getStringOrNull("color")

    inline val visible: Boolean get() = entry.getChar("visible") == 1.toChar()
    inline val group: Boolean get() = entry.getChar("group") == 1.toChar()

    fun toNick(): Nick {
        var prefix = this.prefix
        if (prefix == null || prefix == " ") prefix = ""
        val away = color?.contains("weechat.color.nicklist_away") == true

        return Nick(pointer, prefix, name, away)
    }

    companion object {
        fun makeNicklistRequest(pointer: Long) = "(nicklist) nicklist ${pointer.as0x}"
    }
}


@JvmInline value class NickDiffSpec(val entry: HdataEntry) {
    inline val command: Char get() = entry.getChar("_diff")
}
