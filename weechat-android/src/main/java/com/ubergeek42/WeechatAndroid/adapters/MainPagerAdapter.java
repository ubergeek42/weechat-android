package com.ubergeek42.WeechatAndroid.adapters;


import android.os.Bundle;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class MainPagerAdapter extends PagerAdapter {

    static Logger logger = LoggerFactory.getLogger("MainPagerAdapter");
    final static boolean DEBUG_SUPER = false;
    final static boolean DEBUG_BUFFERS = true;

    ArrayList<String> full_names = new ArrayList<String>();
    ArrayList<BufferFragment> fragments = new ArrayList<BufferFragment>();
    WeechatActivity activity;
    ViewPager pager;
    FragmentManager manager;
    FragmentTransaction transaction = null;

    public MainPagerAdapter(WeechatActivity activity, FragmentManager manager, ViewPager pager) {
        super();
        this.activity = activity;
        this.manager = manager;
        this.pager = pager;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** MUST BE RUN ON MAIN THREAD
     ** switch to already open ui_buffer OR create a new ui_buffer, putting it into BOTH full_names and fragments,
     ** run notifyDataSetChanged() which will in turn call instantiateItem(), and set new ui_buffer as the current one */
    public void openBuffer(final String full_name, final boolean focus, final boolean focus_line) {
        if (DEBUG_BUFFERS) logger.info("openBuffer({}, focus={}, focus line={})", new Object[]{full_name, focus, focus_line});
        BufferFragment fragment;
        int idx = full_names.indexOf(full_name);
        if (idx >= 0) {
            if (focus) pager.setCurrentItem(idx);
            fragment = fragments.get(idx);
        } else {
            Buffer buffer = activity.relay.getBufferByFullName(full_name);
            if (buffer != null)
                buffer.setOpen(true);
            fragment = newBufferFragment(full_name);
            fragments.add(fragment);
            full_names.add(full_name);
            notifyDataSetChanged();
            if (focus) pager.setCurrentItem(full_names.size());
        }
        if (focus_line) fragment.scrollToHotLine();
    }

    private BufferFragment newBufferFragment(String full_name) {
        BufferFragment fragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putString(BufferFragment.LOCAL_PREF_FULL_NAME, full_name);
        fragment.setArguments(args);
        return fragment;
    }

    /** MUST BE RUN ON MAIN THREAD
     ** close buffer if open, removing it from BOTH full_names and fragments.
     ** destroyItem() checks the lists to see if it has to remove the item for good */
    public void closeBuffer(String full_name) {
        if (DEBUG_BUFFERS) logger.info("closeBuffer({})", full_name);
        final int idx = full_names.indexOf(full_name);
        if (idx >= 0) {
            full_names.remove(idx);
            fragments.remove(idx);
            notifyDataSetChanged();
            if (activity.relay != null) {
                Buffer buffer = activity.relay.getBufferByFullName(full_name);
                if (buffer != null) buffer.setOpen(false);
            }
        }
    }

    /** returns true if a buffer is open, i.e. inside the pager
     ** it might not be the one focused */
    public boolean isBufferOpen(final String full_name) {
        return full_names.indexOf(full_name) >= 0;
    }

    /** returns full name of the buffer that is currently focused or null */
    public @Nullable String getCurrentBufferFullName() {
        int i = pager.getCurrentItem();
        return (full_names.size() > i) ? full_names.get(i) : null;
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
        String tag = full_names.get(i);
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
        return full_names.size();
    }

    @Override
    public CharSequence getPageTitle(int i) {
        return fragments.get(i).getShortBufferName();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((Fragment) object).getView() == view;
    }

    private Fragment old_frag;
    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        if (object == old_frag) return;
        Fragment frag = (Fragment) object;
        if (old_frag != null) {
            old_frag.setMenuVisibility(false);
            old_frag.setUserVisibleHint(false);
        }
        if (frag != null) {
            frag.setMenuVisibility(true);
            frag.setUserVisibleHint(true);
        }
        old_frag = frag;
    }

    /** this should return index for fragments or POSITION_NONE if a fragment has been removed
     ** providing proper indexes instead of POSITION_NONE allows buffers not to be
     ** fully recreated on every ui_buffer list change */
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

    private boolean executing_transaction = false;

    /** commit the transaction and execute it at once—in the current loop—if possible
     ** the check is necessary because calls to executePendingTransactions() can't be recursive
     ** if it's not possible to execute now, transaction will be executed some time later
     ** on the main thread */
    @Override
    public void finishUpdate(ViewGroup container) {
        if (transaction == null)
            return;
        transaction.commitAllowingStateLoss();
        transaction = null;
        if (!executing_transaction) {
            executing_transaction = true;
            manager.executePendingTransactions();
            executing_transaction = false;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// save / restore

    static final String FULL_NAMES = "\0";

    @Override public @Nullable Parcelable saveState() {
        if (DEBUG_SUPER) logger.info("saveState()");
        if (fragments.size() == 0)
            return null;
        Bundle state = new Bundle();
        state.putStringArrayList(FULL_NAMES, full_names);
        for (String full_name : full_names) {
            Fragment fragment = manager.findFragmentByTag(full_name);
            if (fragment != null) manager.putFragment(state, full_name, fragment);
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
        full_names = state.getStringArrayList(FULL_NAMES);
        if (full_names.size() > 0) {
            for (String full_name : full_names) {
                BufferFragment fragment = (BufferFragment) manager.getFragment(state, full_name);
                fragments.add((fragment != null) ? fragment : newBufferFragment(full_name));
            }
            notifyDataSetChanged();
        }
    }
}
