// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;


import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.text.TextUtils;

import com.ubergeek42.WeechatAndroid.tabcomplete.TabCompleter;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;


// this class is supposed to be synchronized by Buffer
class Nicks {
    @SuppressWarnings("FieldCanBeLocal")
    final private @Root Kitty kitty = Kitty.make();

    public enum STATUS {INIT, READY}

    STATUS status = STATUS.INIT;

    // sorted in last-spoke-comes-first order
    private final LinkedList<Nick> nicks = new LinkedList<>();

    @WorkerThread Nicks(String name) {
        kitty.setPrefix(name);
    }

    @AnyThread ArrayList<Nick> getCopySortedByPrefixAndName() {
        ArrayList<Nick> out = new ArrayList<>(nicks);
        Collections.sort(out, prefixAndNameComparator);
        return out;
    }

    @WorkerThread void addNick(Nick nick) {
        nicks.add(nick);
    }

    @WorkerThread void removeNick(long pointer) {
        for (Iterator<Nick> it = nicks.iterator(); it.hasNext();) {
            if (it.next().pointer == pointer) {
                it.remove();
                break;
            }
        }
    }

    @WorkerThread void updateNick(Nick nick) {
        for (ListIterator<Nick> it = nicks.listIterator(); it.hasNext();) {
            if (it.next().pointer == nick.pointer) {
                it.set(nick);
                break;
            }
        }
    }

    @AnyThread void clear() {
        nicks.clear();
        status = STATUS.INIT;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread ArrayList<String> getMostRecentNicksMatching(String prefix, String ignoreChars) {
        String lowerCasePrefix = prefix.toLowerCase();
        ignoreChars = removeChars(ignoreChars, lowerCasePrefix);

        ArrayList<String> out = new ArrayList<>(20);
        for (Nick nick : nicks) {
            String lowerCaseNick = nick.name.toLowerCase();
            String lowerCaseNickWithoutIgnoreChars = removeChars(lowerCaseNick, ignoreChars);
            if (lowerCaseNickWithoutIgnoreChars.startsWith(lowerCasePrefix)) out.add(nick.name);
        }
        return out;
    }

    @WorkerThread void bumpNickToTop(@Nullable String name) {
        if (name == null) return;
        for (Iterator<Nick> it = nicks.iterator(); it.hasNext();) {
            Nick nick = it.next();
            if (name.equals(nick.name)) {
                it.remove();
                nicks.addFirst(nick);
                break;
            }
        }
    }

    @WorkerThread void sortNicksByLines(Iterator<Line> it) {
        final HashMap<String, Integer> nameToPosition = new HashMap<>();

        while (it.hasNext()) {
            Line line = it.next();
            if (line.type != LineSpec.Type.IncomingMessage)
                continue;
            String name = line.nick;
            if (name != null && !nameToPosition.containsKey(name))
                nameToPosition.put(name, nameToPosition.size());
        }

        Collections.sort(nicks, (left, right) -> {
            Integer l = nameToPosition.get(left.name);
            Integer r = nameToPosition.get(right.name);
            if (l == null) l = Integer.MAX_VALUE;
            if (r == null) r = Integer.MAX_VALUE;
            return l - r;
        });

        // sorting nicks means that all nicks have been fetched
        status = STATUS.READY;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static Comparator<Nick> prefixAndNameComparator = (n1, n2) -> {
        int diff = prioritizePrefix(n1.prefix) - prioritizePrefix(n2.prefix);
        return (diff != 0) ? diff : n1.name.compareToIgnoreCase(n2.name);
    };

    // lower values = higher priority
    private static int prioritizePrefix(String p) {
        if (TextUtils.isEmpty(p)) return 100;
        switch(p.charAt(0)) {
            case '~': return 1;  // Owners
            case '&': return 2;  // Admins
            case '@': return 3;  // Ops
            case '%': return 4;  // Half-Ops
            case '+': return 5;  // Voiced
            default: return 100; // Other
        }
    }

    private static String removeChars(String string, String chars) {
        for(int i = 0; i < chars.length(); i++) {
            string = string.replace(chars.substring(i, i + 1), "");
        }
        return string;
    }
}
