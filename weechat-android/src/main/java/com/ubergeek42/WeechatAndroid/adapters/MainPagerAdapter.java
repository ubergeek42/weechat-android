package com.ubergeek42.WeechatAndroid.adapters;


import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment;
import com.ubergeek42.WeechatAndroid.service.Buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class MainPagerAdapter extends PagerAdapter {

    private static Logger logger = LoggerFactory.getLogger("MainPagerAdapter");
    final private static boolean DEBUG = BuildConfig.DEBUG;
    final private static boolean DEBUG_SUPER = false;
    final private static boolean DEBUG_BUFFERS = false;

    private boolean phone_mode = false;
    private ArrayList<String> full_names = new ArrayList<String>();
    private ArrayList<Fragment> fragments = new ArrayList<Fragment>();
    private WeechatActivity activity;
    private ViewPager pager;
    private FragmentManager manager;
    private FragmentTransaction transaction = null;


    public MainPagerAdapter(WeechatActivity activity, FragmentManager manager, ViewPager pager) {
        super();
        this.activity = activity;
        this.manager = manager;
        this.pager = pager;
    }

    public void firstTimeInit(boolean phone_mode) {
        this.phone_mode = phone_mode;
        if (phone_mode) {
            full_names.add("");
            fragments.add(new BufferListFragment());
        }
    }

    public @NonNull String getFullNameAt(int i) {
        return full_names.get(i);
    }

    ///////////////////////
    ///////////////////////
    ///////////////////////

    @Override
    public int getCount() {
        return full_names.size();
    }

    /** this can be called either when a new fragment is being added or the old one is being
     ** shown. in both cases the fragment will be in this.fragments, but in the latter case it
     ** will not have been added to the fragment manager */
    @Override
    public Object instantiateItem(ViewGroup container, int i) {
        if (DEBUG_SUPER) logger.info("instantiateItem(..., {})", i);
        if (transaction == null) transaction = manager.beginTransaction();
        String tag = full_names.get(i);
        Fragment frag = manager.findFragmentByTag(tag);
        if (frag != null) {
            if (DEBUG_SUPER) logger.info((phone_mode && i == 0) ? "...show()" : "...attach()");
            if (phone_mode && i == 0) transaction.show(frag); else transaction.attach(frag);
        } else {
            if (DEBUG_SUPER) logger.info("...add()");
            transaction.add(container.getId(), frag = fragments.get(i), tag);
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
            if (DEBUG_SUPER) logger.info((phone_mode && i == 0) ? "...hide()" : "...detach()");
            if (phone_mode && i == 0) transaction.hide(frag); else transaction.detach(frag);
        } else {
            if (DEBUG_SUPER) logger.info("destroyItem(): remove");
            transaction.remove(frag);
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((Fragment) object).getView() == view;
    }

    private Fragment old_frag;

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        Fragment frag = (Fragment) object;
        if (frag == old_frag) return;
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
    @Override
    public int getItemPosition(Object object) {
        int idx = fragments.indexOf(object);
        if (DEBUG_SUPER) logger.info("getItemPosition(...) -> {}", (idx >= 0) ? idx : POSITION_NONE);
        return (idx >= 0) ? idx : POSITION_NONE;
    }

    /** this one's empty because instantiateItem and destroyItem create transactions as needed
     ** this function is called too frequently to create a transaction inside it */
    @Override
    public void startUpdate(ViewGroup container) {}

    /** this function, too, is called way too frequently */
    @Override
    public void finishUpdate(ViewGroup container) {
        if (transaction == null)
            return;
        transaction.commitAllowingStateLoss();
        transaction = null;
        manager.executePendingTransactions();
    }

    @Override
    public CharSequence getPageTitle(int i) {
        return (phone_mode && i == 0) ? "Buffer list" : ((BufferFragment) fragments.get(i)).getShortBufferName();
    }

    /** switch to already open ui_buffer OR create a new ui_buffer, putting it into BOTH full_names and fragments,
     ** run notifyDataSetChanged() which will in turn call instantiateItem(), and set new ui_buffer as the current one */
    public void openBuffer(final String full_name, final boolean focus, final boolean must_focus_hot) {
        if (DEBUG_BUFFERS) logger.info("openBuffer({}, {}, {})", new Object[]{full_name, focus, must_focus_hot});
        int idx = full_names.indexOf(full_name);
        if (idx >= 0) {
            if (focus) pager.setCurrentItem(idx);
            if (must_focus_hot) ((BufferFragment) fragments.get(idx)).maybeScrollToLine(true);
        } else {
            if (activity.relay != null) {
                    Buffer buffer = activity.relay.getBufferByFullName(full_name);
                    if (buffer != null) buffer.setOpen(true);
            }
            fragments.add(newBufferFragment(full_name, must_focus_hot));
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    full_names.add(full_name);
                    notifyDataSetChanged();
                    if (focus) pager.setCurrentItem(full_names.size());
                }
            });
        }
    }

    private Fragment newBufferFragment(String full_name, boolean must_focus_hot) {
        Fragment fragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putString("full_name", full_name);
        if (must_focus_hot) args.putBoolean("must_focus_hot", true);
        fragment.setArguments(args);
        return fragment;
    }

    /** close ui_buffer if open, removing it from BOTH full_names and fragments.
     ** destroyItem() checks the lists to see if it has to remove the item for good */
    public void closeBuffer(String full_name) {
        if (DEBUG_BUFFERS) logger.info("closeBuffer({})", full_name);
        final int idx = full_names.indexOf(full_name);
        if (idx >= 0) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    full_names.remove(idx);
                    fragments.remove(idx);
                    notifyDataSetChanged();
                }
            });
            if (activity.relay != null) {
                Buffer buffer = activity.relay.getBufferByFullName(full_name);
                if (buffer != null) buffer.setOpen(false);
            }
        }
    }

    /** returns BufferFragment that is currently focused
     ** or null if nothing or BufferListFragment is focused */
    public @Nullable BufferFragment getCurrentBufferFragment() {
        int i = pager.getCurrentItem();
        if ((phone_mode && i == 0) || fragments.size() == 0)
            return null;
        else
            return (BufferFragment) fragments.get(i);
    }

    /** the following two methods magically get called on application recreation,
     ** so put all our save/restore state here */
    @Override public @Nullable Parcelable saveState() {
        if (DEBUG_SUPER) logger.info("saveState()");
        if (fragments.size() == 0)
            return null;
        Bundle state = new Bundle();
        state.putStringArrayList("\0", full_names);
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
        full_names = state.getStringArrayList("\0");
        if (full_names.size() > 0) {
            for (String full_name : full_names) {
                Fragment fragment = manager.getFragment(state, full_name);
                fragments.add((fragment != null) ? fragment : newBufferFragment(full_name, false));
            }
            phone_mode = full_names.get(0).equals("");
            notifyDataSetChanged();
        }
    }
}
