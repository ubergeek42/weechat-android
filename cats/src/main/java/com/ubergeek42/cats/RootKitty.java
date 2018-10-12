package com.ubergeek42.cats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import static com.ubergeek42.cats.Cats.disabled;

public class RootKitty extends Kitty {
    @Nullable String prefix;
    private final @NonNull ArrayList<KidKitty> kids = new ArrayList<>();

    RootKitty(@NonNull String tag) {
        super(tag);
        enabled = !disabled.contains(tag);
    }

    @Override public KidKitty kid(@NonNull String tag) {
        for (KidKitty kid : kids) if (kid.tag == tag) return kid;
        KidKitty kid = new KidKitty(this, tag);
        kids.add(kid);
        return kid;
    }

    @Override public void setPrefix(@Nullable String prefix) {
        this.prefix = prefix;
    }

    @Override String getTag() {
        return tag;
    }

    @Override @Nullable String getPrefix() {
        return prefix;
    }
}
