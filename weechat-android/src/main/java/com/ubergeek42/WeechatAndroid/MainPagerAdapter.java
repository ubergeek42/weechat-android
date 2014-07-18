package com.ubergeek42.WeechatAndroid;


import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class MainPagerAdapter extends PagerAdapter {

    private static Logger logger = LoggerFactory.getLogger("MainPagerAdapter");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    private boolean phone_mode = false;
    private ArrayList<String> names = new ArrayList<String>();
    private ArrayList<Fragment> fragments = new ArrayList<Fragment>();
    private ViewPager pager;
    private FragmentManager manager;
    private FragmentTransaction transaction = null;


    public MainPagerAdapter(FragmentManager manager, ViewPager pager) {
        super();
        this.manager = manager;
        this.pager = pager;
    }

    public void firstTimeInit(boolean phone_mode) {
        this.phone_mode = phone_mode;
        if (phone_mode) {
            names.add("");
            fragments.add(new BufferListFragment());
        }
    }

    ///////////////////////
    ///////////////////////
    ///////////////////////

    @Override
    public int getCount() {
        return names.size();
    }

    /** this can be called either when a new fragment is being added or the old one is being
     ** shown. in both cases the fragment will be in this.fragments, but in the latter case it
     ** will not have been added to the fragment manager */
    @Override
    public Object instantiateItem(ViewGroup container, int i) {
        if (DEBUG) logger.info("instantiateItem(..., {})", i);
        if (transaction == null)
            transaction = manager.beginTransaction();
        Fragment f = manager.findFragmentByTag(names.get(i));
        if (f != null) {
            if (DEBUG) logger.info("instantiateItem(): attach");  // show can be used instead
            transaction.attach(f);
        } else {
            f = fragments.get(i);
            if (DEBUG) logger.info("instantiateItem(): add");
            transaction.add(container.getId(), f, names.get(i));
        }
        return f;
    }

    /** this can be called either when a fragment has been removed by closeBuffer or when it's
     ** getting off-screen. in the first case the fragment will still be in this.fragments */
    @Override
    public void destroyItem(ViewGroup container, int i, Object object) {
        if (DEBUG) logger.info("destroyItem(..., {}, {})", i, object);
        if (transaction == null)
            transaction = manager.beginTransaction();
        if (fragments.size() > i && fragments.get(i) == object) {
            if (DEBUG) logger.info("destroyItem(): detach");  // hide can be used instead
            transaction.detach((Fragment) object);
        } else {
            if (DEBUG) logger.info("destroyItem(): remove");
            transaction.remove((Fragment) object);
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((Fragment) object).getView() == view;
    }

    /** this should return index for fragments or POSITION_NONE if a fragment has been removed
     ** providing proper indexes instead of POSITION_NONE allows buffers not to be
     ** fully recreated on every buffer list change */
    @Override
    public int getItemPosition(Object object) {
        int idx = fragments.indexOf(object);
        if (DEBUG) logger.info("getItemPosition(...) -> {}", (idx >= 0) ? idx : POSITION_NONE);
        return (idx >= 0) ? idx : POSITION_NONE;
    }

    /** this one's empty because instantiateItem and destroyItem create transactions as needed
     ** this function is called too frequently to create a transaction inside it*/
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
        return (phone_mode && i == 0) ? "Buffer List" : ((BufferFragment) fragments.get(i)).getShortBufferName();
    }

    /** switch to already open buffer OR create a new buffer, putting it into BOTH names and fragments,
     ** run notifyDataSetChanged() which will in turn call instantiateItem(), and set new buffer as the current one */
    public void openBuffer(String name) {
        if (DEBUG) logger.info("openBuffer({})", name);
        int idx = names.indexOf(name);
        if (idx >= 0) {
            // found buffer by name, switch to it
            pager.setCurrentItem(idx);
        } else {
            // create a new one
            Fragment f = new BufferFragment();
            Bundle args = new Bundle();
            args.putString("buffer", name);
            f.setArguments(args);
            fragments.add(f);
            names.add(name);
            notifyDataSetChanged();
            pager.setCurrentItem(names.size());
        }
    }

    /** close buffer if open, removing it from BOTH names and fragments.
     ** destroyItem() checks the lists to see if it has to remove the item for good */
    public void closeBuffer(String name) {
        if (DEBUG) logger.info("closeBuffer({})", name);
        int idx = names.indexOf(name);
        if (idx >= 0) {
            names.remove(idx);
            fragments.remove(idx);
            notifyDataSetChanged();
        }
    }

    /** returns BufferFragment that is currently focused
     ** or null if nothing or BufferListFragment is focused */
    public BufferFragment getCurrentBuffer() {
        int i = pager.getCurrentItem();
        if ((phone_mode && i == 0) || fragments.size() == 0)
            return null;
        else
            return (BufferFragment) fragments.get(i);
    }

    /** the following two methods magically get called on application recreation,
     ** so put all our save/restore state here */
    @Override
    public Parcelable saveState() {
        if (DEBUG) logger.info("saveState()");
        if (fragments.size() == 0)
            return null;
        Bundle state = new Bundle();
        state.putStringArrayList("\0", names);
        for (int i = 0, size = names.size(); i < size; i++)
            manager.putFragment(state, names.get(i), fragments.get(i));
        return state;
    }

    @Override
    public void restoreState(Parcelable parcel, ClassLoader loader) {
        if (DEBUG) logger.info("restoreState()");
        if (parcel == null)
            return;
        Bundle state = (Bundle) parcel;
        state.setClassLoader(loader);
        names = state.getStringArrayList("\0");
        if (names.size() > 0) {
            for (String name : names)
                fragments.add(manager.getFragment(state, name));
            phone_mode = names.get(0).equals("");
            notifyDataSetChanged();
        }
    }
}
