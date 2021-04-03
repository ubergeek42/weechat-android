package com.ubergeek42.WeechatAndroid.adapters;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.upload.ShareObject;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;


public class MainPagerAdapter extends PagerAdapter {

    final private static @Root Kitty kitty = Kitty.make();

    final private ArrayList<Long> pointers = new ArrayList<>();

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

    @MainThread @CatD public void openBuffer(final long pointer) {
        if (pointers.contains(pointer)) return;
        Buffer buffer = BufferList.findByPointer(pointer);
        if (buffer != null) buffer.setOpen(true, true);
        pointers.add(pointer);
        sortOpenBuffers();
        notifyDataSetChanged();
        P.setBufferOpen(pointer, true);
    }

    @MainThread @CatD public void closeBuffer(final long pointer) {
        if (!pointers.remove(pointer)) return;
        notifyDataSetChanged();
        Buffer buffer = BufferList.findByPointer(pointer);
        if (buffer != null) Weechat.runOnMainThread(() -> buffer.setOpen(false, false)); // make sure isOpen is called after
        P.setBufferOpen(pointer, false);
    }

    @MainThread public void focusBuffer(long pointer) {
        pager.setCurrentItem(pointers.indexOf(pointer));
    }

    // returns whether a buffer is inside the pager
    @MainThread public boolean isBufferOpen(long pointer) {
        return pointers.contains(pointer);
    }

    // returns full name of the buffer that is currently focused or 0 if there's no buffers
    @MainThread public long getCurrentBufferPointer() {
        int i = pager.getCurrentItem();
        return (pointers.size() > i) ? pointers.get(i) : 0;
    }

    // returns BufferFragment that is currently focused or null
    @MainThread public @Nullable BufferFragment getCurrentBufferFragment() {
        return getBufferFragment(pager.getCurrentItem());
    }

    @MainThread private @Nullable BufferFragment getBufferFragment(int i) {
        if (pointers.size() <= i) return null;
        return (BufferFragment) manager.findFragmentByTag(Utils.pointerToString(pointers.get(i)));
    }

    private ArrayList<Long> oldPointers = new ArrayList<>();
    @MainThread public void sortOpenBuffers() {
        long currentPointer = getCurrentBufferPointer();
        BufferList.sortOpenBuffersByBuffers(pointers);
        if (oldPointers.equals(pointers)) return;
        oldPointers = new ArrayList<>(pointers);
        notifyDataSetChanged();
        pager.setCurrentItem(pointers.indexOf(currentPointer));
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////// overrides
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // attach a fragment if it's in the FragmentManager, create and add a new one if it's not
    @MainThread @Override @SuppressLint("CommitTransaction") @Cat(linger=true)
    public @NonNull Object instantiateItem(@NonNull ViewGroup container, int i) {
        if (transaction == null) transaction = manager.beginTransaction();
        String tag = Utils.pointerToString(pointers.get(i));
        Fragment frag = manager.findFragmentByTag(tag);
        if (frag == null) {
            kitty.trace("adding");
            transaction.add(container.getId(), frag = BufferFragment.newInstance(pointers.get(i)), tag);
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
        if (pointers.contains(Utils.pointerFromString(frag.getTag()))) {
            kitty.trace("detaching");
            transaction.detach(frag);
        } else {
            kitty.trace("removing");
            transaction.remove(frag);
        }
        if (pointers.isEmpty()) oldFrag = null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Override public int getCount() {
        return pointers.size();
    }

    @MainThread @Override public CharSequence getPageTitle(int i) {
        long pointer = pointers.get(i);
        Buffer buffer = BufferList.findByPointer(pointer);
        return buffer == null ? Utils.pointerToString(pointer) : buffer.shortName;
    }

    @MainThread @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return ((Fragment) object).getView() == view;
    }

    private Fragment oldFrag;

    // in the interface object is annotates as @NonNull but it can be nullable
    // see https://issuetracker.google.com/issues/69440293
    // todo: has this issue been resolved?
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
        int idx = pointers.indexOf(Utils.pointerFromString(((Fragment) object).getTag()));
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
        return P.openBuffers.size() > 0 && pointers.size() == 0 && BufferList.hasData();
    }

    @MainThread public void restoreBuffers() {
        for (long pointer : P.openBuffers)
            openBuffer(pointer);
    }
}
