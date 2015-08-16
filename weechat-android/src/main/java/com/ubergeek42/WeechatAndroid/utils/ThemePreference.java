package com.ubergeek42.WeechatAndroid.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Environment;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by ubergeek on 8/15/15.
 */
public class ThemePreference extends DialogPreference implements DialogInterface.OnClickListener {
    // Keeps the theme file paths and names in separate arrays
    private List< String >    m_themePaths;
    private List< String >    m_themeNames;
    public ThemePreference( Context context, AttributeSet attrs )
    {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder( AlertDialog.Builder builder )
    {
        super.onPrepareDialogBuilder(builder);
        m_themePaths = new ArrayList< String >();
        m_themeNames = new ArrayList< String >();

        AssetManager assetManager = getContext().getAssets();
        try {
            String[] builtin_themes = assetManager.list("");
            for (String theme: builtin_themes) {
                // Only get *theme.properties.files
                if (!theme.toLowerCase().endsWith("theme.properties")) {
                    continue;
                }
                Properties p = new Properties();
                try {
                    p.load(assetManager.open(theme));
                } catch (IOException e) {
                    // Failed to load file
                    Log.d("ThemePreference", "Failed to load file from assets " + theme);
                    continue;
                }

                m_themePaths.add(theme);
                m_themeNames.add(p.getProperty("NAME", theme));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Find all the preferences files
        String searchDir = Environment.getExternalStorageDirectory().toString() + "/weechat";
        File dir = new File( searchDir );
        if ( dir.exists() ) {
            File[] files = dir.listFiles();
            if (files != null) {
                // Only get *theme.properties.files
                for (File file : files) {
                    if (file.getName().toLowerCase().endsWith("theme.properties")) {

                        Properties p = new Properties();
                        try {
                            InputStream is = new FileInputStream(file);
                            p.load(is);
                        } catch (IOException e) {
                            // Failed to load file
                            Log.d("ThemePreference", "Failed to load file " + file.getAbsolutePath());
                            continue;
                        }
                        m_themePaths.add(file.getAbsolutePath());
                        m_themeNames.add(p.getProperty("NAME", file.getName()));
                    }
                }
            }
        }
        int checked_item = 0;
        int idx = 0;
        String selectedThemePath = getSharedPreferences().getString( getKey(), "");
        for (String path: m_themePaths) {
            if ( path.equals( selectedThemePath ) )
                checked_item = idx;
            idx++;
        }
        // Create out adapter
        // If you're building for API 11 and up, you can pass builder.getContext
        // instead of current context
        ThemePreference.ThemeAdapter adapter = new ThemeAdapter();

        builder.setSingleChoiceItems( adapter, checked_item, this );

        // The typical interaction for list-based dialogs is to have click-on-an-item dismiss the dialog
        builder.setPositiveButton(null, null);
    }

    public void onClick(DialogInterface dialog, int which)
    {
        if ( which >=0 && which < m_themePaths.size() )
        {
            String selectedThemePath = m_themePaths.get( which );
            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putString( getKey(), selectedThemePath );
            editor.commit();

            dialog.dismiss();
        }
    }

    public class ThemeAdapter  extends BaseAdapter {
        @Override
        public int getCount() {
            return m_themeNames.size();
        }

        @Override
        public Object getItem(int i) {
            return m_themeNames.get(i);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView( int position, View convertView, ViewGroup parent ) {
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

                // If you want to make the selected item having different foreground or background color,
                // be aware of themes. In some of them your foreground color may be the background color.
                // So we don't mess with anything here and just add the extra stars to have the selected
                // font to stand out.
                tv.setText(m_themeNames.get( position ) );
            }

            return view;
        }
    }
}
