package com.ubergeek42.cats;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import static com.ubergeek42.cats.Cats.disabled;


class KidKitty extends Kitty {
    private final @NonNull RootKitty mom;

    KidKitty(@NonNull RootKitty mom, @NonNull String tag) {
        super(tag);
        this.mom = mom;
        enabled = !(disabled.contains(mom.tag) ||
                    disabled.contains(mom.tag + "/" + tag) ||
                    disabled.contains("/" + tag));
    }

    @Override String getTag() {
        return mom.tag + "/" + this.tag;
    }

    @Override @Nullable String getPrefix() {
        return mom.prefix;
    }

    @Override public KidKitty kid(@NonNull String tag) {
        return mom.kid(tag);
    }

    @Override public void setPrefix(@Nullable String prefix) {
        mom.setPrefix(prefix);
    }
}
