package com.ubergeek42.WeechatAndroid.utils;

/*
 * Copyright (C) 2011-2012 George Yunaev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

public class FontPreference extends DialogPreference implements DialogInterface.OnClickListener
{
    // Keeps the font file paths and names in separate arrays
    private List< String >    m_fontPaths;
    private List< String >    m_fontNames;

    // Font adaptor responsible for redrawing the item TextView with the appropriate font.
    // We use BaseAdapter since we need both arrays, and the effort is quite small.
    public class FontAdapter extends BaseAdapter
    {
        @Override
        public int getCount()
        {
            return m_fontNames.size();
        }

        @Override
        public Object getItem(int position)
        {
            return m_fontNames.get( position );
        }

        @Override
        public long getItemId(int position)
        {
            // We use the position as ID
            return position;
        }

        @Override
        public View getView( int position, View convertView, ViewGroup parent )
        {
            View view = convertView;

            // This function may be called in two cases: a new view needs to be created,
            // or an existing view needs to be reused
            if ( view == null )
            {
                // Since we're using the system list for the layout, use the system inflater
                final LayoutInflater inflater = (LayoutInflater)
                        getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );

                // And inflate the view android.R.layout.select_dialog_singlechoice
                // Why? See com.android.internal.app.AlertController method createListView()
                view = inflater.inflate( android.R.layout.select_dialog_singlechoice, parent, false);
            }

            if ( view != null )
            {
                // Find the text view from our interface
                CheckedTextView tv = (CheckedTextView) view.findViewById( android.R.id.text1 );

                // Position 0 is the system default font
                if (position == 0 ) {
                    tv.setTypeface(Typeface.MONOSPACE);
                } else {
                    // Replace the string with the current font name using our typeface
                    Typeface tface = Typeface.createFromFile(m_fontPaths.get(position));
                    tv.setTypeface(tface);
                }

                // If you want to make the selected item having different foreground or background color,
                // be aware of themes. In some of them your foreground color may be the background color.
                // So we don't mess with anything here and just add the extra stars to have the selected
                // font to stand out.
                tv.setText(m_fontNames.get( position ) );
            }

            return view;
        }
    }

    public FontPreference( Context context, AttributeSet attrs )
    {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder( Builder builder )
    {
        super.onPrepareDialogBuilder(builder);

        // Get the fonts on the device
        HashMap< String, String > fonts = FontManager.enumerateFonts();
        m_fontPaths = new ArrayList< String >();
        m_fontNames = new ArrayList< String >();
        m_fontPaths.add(null);
        m_fontNames.add("System Default");

        // Get the current value to find the checked item
        String selectedFontPath = getSharedPreferences().getString( getKey(), "");
        int idx = 1, checked_item = 0; // idx starts at one since we inserted our own thing first

        List<String> sortedFonts = new ArrayList<String>(fonts.keySet());
        Collections.sort(sortedFonts);

        for ( String path : sortedFonts )
        {
            if ( path.equals( selectedFontPath ) )
                checked_item = idx;

            m_fontPaths.add( path );
            m_fontNames.add( fonts.get(path) );
            idx++;
        }

        // Create out adapter
        // If you're building for API 11 and up, you can pass builder.getContext
        // instead of current context
        FontAdapter adapter = new FontAdapter();

        builder.setSingleChoiceItems( adapter, checked_item, this );

        // The typical interaction for list-based dialogs is to have click-on-an-item dismiss the dialog
        builder.setPositiveButton(null, null);
    }

    public void onClick(DialogInterface dialog, int which)
    {
        if ( which >=0 && which < m_fontPaths.size() )
        {
            String selectedFontPath = m_fontPaths.get( which );
            Editor editor = getSharedPreferences().edit();
            editor.putString( getKey(), selectedFontPath );
            editor.commit();

            dialog.dismiss();
        }
    }
}