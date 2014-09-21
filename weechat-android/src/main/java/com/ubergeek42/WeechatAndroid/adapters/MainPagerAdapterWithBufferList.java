package com.ubergeek42.WeechatAndroid.adapters;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.ViewGroup;

import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment;

public class MainPagerAdapterWithBufferList extends MainPagerAdapterAbs {

    private final BufferListFragment buffer_list_fragment;

    public MainPagerAdapterWithBufferList(WeechatActivity activity, FragmentManager manager, ViewPager pager) {
        super(activity, manager, pager);
        buffer_list_fragment = new BufferListFragment();
    }

    int getOffset() {
        return 1;
    }

    public @NonNull String getFullNameAt(int i) { //TODO
        return i == 0 ? "" : full_names.get(i - 1);
    }

    @Override
    public int getCount() {
        return full_names.size() + 1;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int i) {
        super.instantiateItem(container, i);
        if (i == 0) return addOrAttachOrShow(container, "", buffer_list_fragment, SHOW);
        else return addOrAttachOrShow(container, full_names.get(i - 1), fragments.get(i - 1), ATTACH);
    }

    @Override
    public void destroyItem(ViewGroup container, int i, Object object) {
        super.destroyItem(container, i, object);
        Fragment frag = (Fragment) object;
        if (i == 0) transaction.hide(frag);
        else removeOrDetach(frag, i - 1);
    }

    @Override
    public int getItemPosition(Object object) {
        if (object.equals(buffer_list_fragment)) return 0;
        int idx = fragments.indexOf(object);
        return (idx >= 0) ? idx + 1 : POSITION_NONE;
    }


    @Override
    public CharSequence getPageTitle(int i) {
        if (i == 0) return "Buffer list";
        return ((BufferFragment) fragments.get(i - 1)).getShortBufferName();
    }

    @Override
    public void openBufferList() {}

    public @Nullable BufferFragment getCurrentBufferFragment() {
        int i = pager.getCurrentItem() - 1;
        if (i < 0 || i >= fragments.size()) return null;
        else return (BufferFragment) fragments.get(i);
    }
}
