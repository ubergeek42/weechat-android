package com.ubergeek42.WeechatAndroid;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;

import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment;

public class MainPagerAdapter extends FragmentStatePagerAdapter {
    private static Logger logger = LoggerFactory.getLogger(MainPagerAdapter.class);
    private BufferListFragment bufferListFragment = null;
    private ArrayList<BufferFragment> buffers = new ArrayList<BufferFragment>();
    private ViewPager pager;
    
    public MainPagerAdapter(FragmentManager fm) throws Exception {
        super(fm);
        throw new Exception("Should not ever be used!");
    }
    
    public MainPagerAdapter(FragmentManager fm, ViewPager p) {
        super(fm);
        pager = p;
    }
    public void setBuffers(ArrayList<BufferFragment> frags) {
        buffers = frags;
    }

    public void setBufferList(BufferListFragment blf) {
        bufferListFragment = blf;
    }

    @Override
    public int getCount() {
        if (bufferListFragment==null)
            return buffers.size();
        else
            return 1+buffers.size();
    }

    @Override
    public Fragment getItem(int pos) {
        // Tablet view
        if (bufferListFragment==null)
            return buffers.get(pos);
        
        // Not tablet view
        if (pos==0) {
            return bufferListFragment;
        } else {
            return buffers.get(pos-1);
        }
    }
    @Override
    public CharSequence getPageTitle(int pos) {
        // Tablet view
        if (bufferListFragment==null) {
            String title = buffers.get(pos).getShortBufferName();
            if (title==null || title.equals("")){
                return buffers.get(pos).getBufferName();
            }
            return title;
        }
            
        // Phone view
        if (pos==0) {
            return "Buffer List";
        } else {
            String title = buffers.get(pos-1).getShortBufferName();
            if (title==null || title.equals("")){
                return buffers.get(pos-1).getBufferName();
            }
            return title;
        }
    }
    
    public void closeBuffer(String buffer) {
        int removeIndex = -1;
        for (int i=0;i<buffers.size(); i++) {
            BufferFragment bf = buffers.get(i);
            if (bf.getBufferName().equals(buffer)) {
                // TODO: wrong thread error for pager.setCurrentItem
                pager.setCurrentItem(i);
                removeIndex = i;
            }
        }
        if (removeIndex!=-1) {
            buffers.remove(removeIndex);
        }
    }
    public void openBuffer(String buffer) {
        // Find the appropriate buffer in our list
        for (int i=0;i<buffers.size(); i++) {
            BufferFragment bf = buffers.get(i);
            if (bf.getBufferName().equals(buffer)) {
                
                if (bufferListFragment == null) {
                    pager.setCurrentItem(i);
                } else {
                    pager.setCurrentItem(i+1);
                }
                // TODO: update title?
                return;
            }
        }

        // Create fragment for the buffer and setup the arguments
        BufferFragment newFragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putString("buffer", buffer);
        newFragment.setArguments(args);
        buffers.add(newFragment);
        pager.setCurrentItem(buffers.size());
    }

    public BufferFragment getCurrentBuffer() {
        int pos = pager.getCurrentItem();
        
        if (bufferListFragment == null) {
            // Tablet view
            if (pos>=0 && pos < buffers.size()) {
                return buffers.get(pos);
            }
        } else {
            // Phone view
            if (pos>=1 && pos <= buffers.size()) { 
                return buffers.get(pos-1);
            }
        }
        return null;
    }
    
}
