package com.ubergeek42.WeechatAndroid.adapters;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class MainPagerAdapter extends PagerAdapter {

    static Logger logger = LoggerFactory.getLogger("MainPagerAdapter");
    final private static boolean DEBUG_SUPER = false;
    final private static boolean DEBUG_BUFFERS = true;

    private ArrayList<String> fullNames = new ArrayList<>();
    private ArrayList<BufferFragment> fragments = new ArrayList<>();

    final private WeechatActivity activity;
    final private ViewPager pager;
    final private FragmentManager manager;
    final private Handler handler;

    FragmentTransaction transaction = null;

    public MainPagerAdapter(WeechatActivity activity, FragmentManager manager, ViewPager pager) {
        super();
        this.activity = activity;
        this.manager = manager;
        this.pager = pager;
        handler = new Handler(Looper.getMainLooper());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** MUST BE RUN ON MAIN THREAD
     ** switch to already open uiBuffer OR create a new uiBuffer, putting it into BOTH fullNames and fragments,
     ** run notifyDataSetChanged() which will in turn call instantiateItem(), and set new uiBuffer as the current one */
    public void openBuffer(final String fullName, final boolean focus) {
        if (DEBUG_BUFFERS) logger.info("openBuffer({}, focus={})", fullName, focus);
        int idx = fullNames.indexOf(fullName);
        if (idx >= 0) {
            if (focus) pager.setCurrentItem(idx);
        } else {
            Buffer buffer = BufferList.findByFullName(fullName);
            if (buffer != null)
                buffer.setOpen(true);
            BufferFragment fragment = newBufferFragment(fullName);
            fragments.add(fragment);
            fullNames.add(fullName);
            notifyDataSetChanged();
            if (focus) pager.setCurrentItem(fullNames.size());
        }
    }

    private BufferFragment newBufferFragment(String fullName) {
        BufferFragment fragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putString(BufferFragment.LOCAL_PREF_FULL_NAME, fullName);
        fragment.setArguments(args);
        return fragment;
    }

    /** MUST BE RUN ON MAIN THREAD
     ** close buffer if open, removing it from BOTH fullNames and fragments.
     ** destroyItem() checks the lists to see if it has to remove the item for good */
    public void closeBuffer(String fullName) {
        if (DEBUG_BUFFERS) logger.info("closeBuffer({})", fullName);
        final int idx = fullNames.indexOf(fullName);
        if (idx >= 0) {
            fullNames.remove(idx);
            fragments.remove(idx);
            notifyDataSetChanged();
            Buffer buffer = BufferList.findByFullName(fullName);
            if (buffer != null) buffer.setOpen(false);
        }
    }

    /** returns true if a buffer is open, i.e. inside the pager
     ** it might not be the one focused */
    public boolean isBufferOpen(final String fullName) {
        return fullNames.indexOf(fullName) >= 0;
    }

    /** returns full name of the buffer that is currently focused or null */
    public @Nullable String getCurrentBufferFullName() {
        int i = pager.getCurrentItem();
        return (fullNames.size() > i) ? fullNames.get(i) : null;
    }

    /** returns BufferFragment that is currently focused or null */
    public @Nullable BufferFragment getCurrentBufferFragment() {
        int i = pager.getCurrentItem();
        return (fragments.size() > i) ? fragments.get(i) : null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// super methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** this can be called either when a new fragment is being added or the old one is being
     ** shown. in both cases the fragment will be in this.fragments, but in the latter case it
     ** will not have been added to the fragment manager */
    @Override
    public Object instantiateItem(ViewGroup container, int i) {
        if (DEBUG_SUPER) logger.info("instantiateItem(..., {})", i);
        if (transaction == null) transaction = manager.beginTransaction();
        String tag = fullNames.get(i);
        Fragment frag = manager.findFragmentByTag(tag);
        if (frag == null) {
            if (DEBUG_SUPER) logger.info("...add");
            transaction.add(container.getId(), frag = fragments.get(i), tag);
        } else {
            if (DEBUG_SUPER) logger.info("...attach");
            transaction.attach(frag);
        }
        return frag;
    }

    /** this can be called either when a fragment has been removed by closeBuffer or when it's
     ** getting off-screen. in the first case the fragment will still be in this.fragments */
    @Override
    public void destroyItem(ViewGroup container, int i, Object object) {
        if (DEBUG_SUPER) logger.info("destroyItem(..., {}, {})", i, object);
        if (transaction == null) transaction = manager.beginTransaction();
        Fragment frag = (Fragment) object;
        if (fragments.size() > i && fragments.get(i) == frag) {
            if (DEBUG_SUPER) logger.info("...detach");
            transaction.detach(frag);
        } else {
            if (DEBUG_SUPER) logger.info("...remove");
            transaction.remove(frag);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int getCount() {
        return fullNames.size();
    }

    @Override
    public CharSequence getPageTitle(int i) {
        return fragments.get(i).getShortBufferName();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((Fragment) object).getView() == view;
    }

    private Fragment oldFrag;
    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
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

    /** this should return index for fragments or POSITION_NONE if a fragment has been removed
     ** providing proper indexes instead of POSITION_NONE allows buffers not to be
     ** fully recreated on every uiBuffer list change */
    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public int getItemPosition(Object object) {
        int idx = fragments.indexOf(object);
        return (idx >= 0) ? idx : POSITION_NONE;
    }

    /** this one's empty because instantiateItem and destroyItem create transactions as needed
     ** this function is called too frequently to create a transaction inside it */
    @Override
    public void startUpdate(ViewGroup container) {}

    /** commit the transaction and execute it ASAP, but NOT on the current loop */
    @Override
    public void finishUpdate(ViewGroup container) {
        if (transaction == null)
            return;
        transaction.commitAllowingStateLoss();
        transaction = null;
        handler.postAtFrontOfQueue(new Runnable() {
            @Override public void run() {
                manager.executePendingTransactions();
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// save / restore

    static final String FULL_NAMES = "\0";

    @Override public @Nullable Parcelable saveState() {
        if (DEBUG_SUPER) logger.info("saveState()");
        if (fragments.size() == 0)
            return null;
        Bundle state = new Bundle();
        state.putStringArrayList(FULL_NAMES, fullNames);
        for (String fullName : fullNames) {
            Fragment fragment = manager.findFragmentByTag(fullName);
            if (fragment != null) manager.putFragment(state, fullName, fragment);
        }
        return state;
    }

    @Override
    public void restoreState(Parcelable parcel, ClassLoader loader) {
        if (DEBUG_SUPER) logger.info("restoreState()");
        if (parcel == null)
            return;
        Bundle state = (Bundle) parcel;
        state.setClassLoader(loader);
        fullNames = state.getStringArrayList(FULL_NAMES);
        if (fullNames.size() > 0) {
            for (String fullName : fullNames) {
                BufferFragment fragment = (BufferFragment) manager.getFragment(state, fullName);
                fragments.add((fragment != null) ? fragment : newBufferFragment(fullName));
            }
            notifyDataSetChanged();
        }
    }
}
