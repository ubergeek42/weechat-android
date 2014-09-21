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
import com.ubergeek42.WeechatAndroid.service.Buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public abstract class MainPagerAdapterAbs extends PagerAdapter {

    static Logger logger = LoggerFactory.getLogger("MainPagerAdapter");
    final static boolean DEBUG = BuildConfig.DEBUG;
    final static boolean DEBUG_SUPER = true;
    final static boolean DEBUG_BUFFERS = true;

    ArrayList<String> full_names = new ArrayList<String>();
    ArrayList<Fragment> fragments = new ArrayList<Fragment>();
    WeechatActivity activity;
    ViewPager pager;
    FragmentManager manager;
    FragmentTransaction transaction = null;

    public MainPagerAdapterAbs(WeechatActivity activity, FragmentManager manager, ViewPager pager) {
        super();
        this.activity = activity;
        this.manager = manager;
        this.pager = pager;
    }

    public abstract @NonNull String getFullNameAt(int i);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public abstract int getCount();

    //////////////////////////////////////////////////////////////////////////////////////////////// instantiation / destruction

    /** this can be called either when a new fragment is being added or the old one is being
     ** shown. in both cases the fragment will be in this.fragments, but in the latter case it
     ** will not have been added to the fragment manager */
    @Override
    public Object instantiateItem(ViewGroup container, int i) {
        if (DEBUG_SUPER) logger.info("instantiateItem(..., {})", i);
        if (transaction == null) transaction = manager.beginTransaction();
        return null;
        // !
    }

    /** this can be called either when a fragment has been removed by closeBuffer or when it's
     ** getting off-screen. in the first case the fragment will still be in this.fragments */
    @Override
    public void destroyItem(ViewGroup container, int i, Object object) {
        if (DEBUG_SUPER) logger.info("destroyItem(..., {}, {})", i, object);
        if (transaction == null) transaction = manager.beginTransaction();
        // !
    }

    final static int SHOW = 1;
    final static int ATTACH = 2;

    /** if fragment known by tag 'tag' is not found in the manager, add it ('fragment')
     ** if it /is/ found, show or attach it, according to alternative action */
    Fragment addOrAttachOrShow(ViewGroup container, @NonNull String tag, Fragment fragment, int alt_action) {
        Fragment frag = manager.findFragmentByTag(tag);
        if (DEBUG_SUPER) logger.info("...action = {}", (frag == null) ? 0 : alt_action);
        if (frag == null)
            transaction.add(container.getId(), frag = fragment, tag);
        else if (alt_action == ATTACH)
            transaction.attach(frag);
        else if (alt_action == SHOW)
            transaction.show(frag);
        return frag;
    }

    /** if BufferFragment fragment corresponds to position in the list, detach it
     ** else it's been deleted, remove */
    void removeOrDetach(Fragment frag, int i) {
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
    public abstract int getItemPosition(Object object);

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
    public abstract CharSequence getPageTitle(int i);

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

    public abstract void openBufferList();

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
    public abstract @Nullable BufferFragment getCurrentBufferFragment();

    /** represents number at which BufferFragments start */
    abstract int getOffset();

    static final String FULL_NAMES = "\0";
    static final String OLD_OFFSET = "\1";
    static final String OLD_PAGE = "\2";

    /** the following two methods magically get called on application recreation */
    @Override public @Nullable Parcelable saveState() {
        if (DEBUG_SUPER) logger.info("saveState()");
        if (fragments.size() == 0)
            return null;
        Bundle state = new Bundle();
        state.putStringArrayList(FULL_NAMES, full_names);
        state.putInt(OLD_OFFSET, getOffset());
        state.putInt(OLD_PAGE, pager.getCurrentItem());
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
                Fragment fragment = manager.getFragment(state, full_name);
                fragments.add((fragment != null) ? fragment : newBufferFragment(full_name, false));
            }
            notifyDataSetChanged();

            // restore page in case BufferListFragment was added or removed
            final int delta = getOffset() - state.getInt(OLD_OFFSET);
            if (delta != 0) {
                final int new_page = state.getInt(OLD_PAGE) + delta;
                pager.post(new Runnable() {
                    @Override
                    public void run() {
                        pager.setCurrentItem(new_page);
                    }
                });
            }
        }
    }
}
