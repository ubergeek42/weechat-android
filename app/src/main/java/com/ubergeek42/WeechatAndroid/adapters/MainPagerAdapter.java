package com.ubergeek42.WeechatAndroid.adapters;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;


public class MainPagerAdapter extends PagerAdapter {

    final private static @Root Kitty kitty = Kitty.make();

    final private ArrayList<String> names = new ArrayList<>();

    final private ViewPager pager;
    final private FragmentManager manager;
    final private Handler handler;

    private FragmentTransaction transaction = null;

    @MainThread public MainPagerAdapter(FragmentManager manager, ViewPager pager) {
        super();
        this.manager = manager;
        this.pager = pager;
        handler = new Handler(Looper.getMainLooper());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @CatD public void openBuffer(final String name) {
        if (names.contains(name)) return;
        Buffer buffer = BufferList.findByFullName(name);
        if (buffer != null) buffer.setOpen(true);
        names.add(name);
        if (P.sortOpenBuffers) BufferList.sortFullNames(names);
        notifyDataSetChanged();
        P.setBufferOpen(name, true);
    }

    @MainThread @CatD public void closeBuffer(String name) {
        if (!names.remove(name)) return;
        notifyDataSetChanged();
        Buffer buffer = BufferList.findByFullName(name);
        if (buffer != null) Weechat.runOnMainThread(() -> buffer.setOpen(false)); // make sure isOpen is called after
        P.setBufferOpen(name, false);
    }

    @MainThread public void focusBuffer(String name) {
        pager.setCurrentItem(names.indexOf(name));
    }

    @MainThread public void setBufferInputText(@NonNull final String name, @NonNull final String text) {
        BufferFragment bufferFragment = getBufferFragment(names.indexOf(name));
        if (bufferFragment == null) {
            kitty.warn("Tried to set input text of unknown buffer %s", name);
            return;
        }
        bufferFragment.setText(text);
    }

    // returns whether a buffer is inside the pager
    @MainThread public boolean isBufferOpen(String name) {
        return names.contains(name);
    }

    // returns full name of the buffer that is currently focused or null if there's no buffers
    @MainThread public @Nullable String getCurrentBufferFullName() {
        int i = pager.getCurrentItem();
        return (names.size() > i) ? names.get(i) : null;
    }

    // returns BufferFragment that is currently focused or null
    @MainThread public @Nullable BufferFragment getCurrentBufferFragment() {
        return getBufferFragment(pager.getCurrentItem());
    }

    @MainThread private @Nullable BufferFragment getBufferFragment(int i) {
        if (names.size() <= i) return null;
        return (BufferFragment) manager.findFragmentByTag(names.get(i));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////// overrides
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // attach a fragment if it's in the FragmentManager, create and add a new one if it's not
    @MainThread @Override @SuppressLint("CommitTransaction") @Cat(linger=true)
    public @NonNull Object instantiateItem(@NonNull ViewGroup container, int i) {
        if (transaction == null) transaction = manager.beginTransaction();
        String tag = names.get(i);
        Fragment frag = manager.findFragmentByTag(tag);
        if (frag == null) {
            kitty.trace("adding");
            transaction.add(container.getId(), frag = BufferFragment.newInstance(tag), tag);
        } else {
            kitty.trace("attaching");
            transaction.attach(frag);
        }
        return frag;
    }

    // detach fragment if it went off-screen or remove it completely if it's been closed by user
    @MainThread @Override @SuppressLint("CommitTransaction") @Cat(linger=true)
    public void destroyItem(@NonNull ViewGroup container, int i, @NonNull Object object) {
        if (transaction == null) transaction = manager.beginTransaction();
        Fragment frag = (Fragment) object;
        if (names.contains(frag.getTag())) {
            kitty.trace("detaching");
            transaction.detach(frag);
        } else {
            kitty.trace("removing");
            transaction.remove(frag);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Override public int getCount() {
        return names.size();
    }

    @MainThread @Override public CharSequence getPageTitle(int i) {
        String name = names.get(i);
        Buffer buffer = BufferList.findByFullName(names.get(i));
        return buffer == null ? name : buffer.shortName;
    }

    @MainThread @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return ((Fragment) object).getView() == view;
    }

    private Fragment oldFrag;

    // in the interface object is annotates as @NonNull but it can be nullable
    // see https://issuetracker.google.com/issues/69440293
    @SuppressWarnings("NullableProblems")
    @MainThread @Override public void setPrimaryItem(@NonNull ViewGroup container, int position, @Nullable Object object) {
        if (object == oldFrag) return;
        Fragment frag = (Fragment) object;
        if (oldFrag != null) {
            oldFrag.setMenuVisibility(false);
            oldFrag.setUserVisibleHint(false);
        }
        if (frag != null) {
            frag.setMenuVisibility(true);
            frag.setUserVisibleHint(true);
        }
        oldFrag = frag;
    }

    // this should return index for fragments or POSITION_NONE if a fragment has been removed
    // providing proper indexes instead of POSITION_NONE allows buffers not to be
    // fully recreated on every uiBuffer list change
    @MainThread @Override public int getItemPosition(@NonNull Object object) {
        int idx = names.indexOf(((Fragment) object).getTag());
        return (idx >= 0) ? idx : POSITION_NONE;
    }

    // this one's empty because instantiateItem and destroyItem create transactions as needed
    // this function is called too frequently to create a transaction inside it
    @MainThread @Override public void startUpdate(@NonNull ViewGroup container) {}

    // commit the transaction and execute it ASAP, but NOT on the current loop
    // this way the drawer will wait for the fragment to appear
    @MainThread @Override public void finishUpdate(@NonNull ViewGroup container) {
        if (transaction == null)
            return;
        transaction.commitAllowingStateLoss();
        transaction = null;
        handler.postAtFrontOfQueue(manager::executePendingTransactions);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread public boolean canRestoreBuffers() {
        return P.openBuffers.size() > 0 && names.size() == 0 && BufferList.hasData();
    }

    @MainThread public void restoreBuffers() {
        for (String fullName : P.openBuffers)
            openBuffer(fullName);
    }
}
