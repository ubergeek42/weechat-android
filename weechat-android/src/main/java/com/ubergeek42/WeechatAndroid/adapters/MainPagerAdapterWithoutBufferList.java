package com.ubergeek42.WeechatAndroid.adapters;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.ViewGroup;

import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;

public class MainPagerAdapterWithoutBufferList extends MainPagerAdapterAbs {

    public MainPagerAdapterWithoutBufferList(WeechatActivity activity, FragmentManager manager, ViewPager pager) {
        super(activity, manager, pager);
    }

    int getOffset() {
        return 0;
    }

    public @NonNull String getFullNameAt(int i) {
        return full_names.get(i);
    }

    @Override
    public int getCount() {
        return full_names.size();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int i) {
        super.instantiateItem(container, i);
        return addOrAttachOrShow(container, full_names.get(i), fragments.get(i), ATTACH);
    }

    @Override
    public void destroyItem(ViewGroup container, int i, Object object) {
        super.destroyItem(container, i, object);
        removeOrDetach((Fragment) object, i);
    }

    @Override
    public int getItemPosition(Object object) {
        int idx = fragments.indexOf(object);
        return (idx >= 0) ? idx : POSITION_NONE;
    }


    @Override
    public CharSequence getPageTitle(int i) {
        return ((BufferFragment) fragments.get(i)).getShortBufferName();
    }

    @Override
    public void openBufferList() {}

    public @Nullable BufferFragment getCurrentBufferFragment() {
        int i = pager.getCurrentItem();
        return (fragments.size() > i) ? (BufferFragment) fragments.get(i) : null;
    }
}
