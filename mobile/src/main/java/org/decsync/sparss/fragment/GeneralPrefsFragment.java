/**
 * spaRSS
 * <p/>
 * Copyright (c) 2015-2016 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * <p/>
 * <p/>
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 * <p/>
 * Copyright (c) 2010-2012 Stefan Handschuh
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.decsync.sparss.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import org.decsync.sparss.MainApplication;
import org.decsync.sparss.R;
import org.decsync.sparss.service.DecsyncService;
import org.decsync.sparss.service.RefreshService;
import org.decsync.sparss.utils.DecsyncUtils;
import org.decsync.sparss.utils.PrefUtils;

import static org.decsync.library.DecsyncKt.getDefaultDecsyncBaseDir;
import static org.decsync.sparss.activity.GeneralPrefsActivity.PERMISSIONS_REQUEST_DECSYNC;

public class GeneralPrefsFragment extends PreferenceFragment {

    private static final int CHOOSE_DECSYNC_DIRECTORY = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.general_preferences);

        setRingtoneSummary();
        setDecsyncDirSummary();

        Preference preference = findPreference(PrefUtils.REFRESH_ENABLED);
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Activity activity = getActivity();
                if (activity != null) {
                    if (Boolean.TRUE.equals(newValue)) {
                        activity.startService(new Intent(activity, RefreshService.class));
                    } else {
                        PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);
                        activity.stopService(new Intent(activity, RefreshService.class));
                    }
                }
                return true;
            }
        });

        preference = findPreference(PrefUtils.LIGHT_THEME);
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefUtils.putBoolean(PrefUtils.LIGHT_THEME, Boolean.TRUE.equals(newValue));

                PreferenceManager.getDefaultSharedPreferences(MainApplication.getContext()).edit().commit(); // to be sure all prefs are written

                android.os.Process.killProcess(android.os.Process.myPid()); // Restart the app

                // this return statement will never be reached
                return true;
            }
        });

        preference = findPreference(PrefUtils.DECSYNC_ENABLED);
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Activity activity = getActivity();
                if (activity == null) {
                    return false;
                }
                if (Boolean.TRUE.equals(newValue)) {
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        DecsyncUtils.INSTANCE.initSync(activity);
                    } else {
                        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_DECSYNC);
                        return false;
                    }
                } else {
                    activity.stopService(new Intent(activity, DecsyncService.class));
                }
                return true;
            }
        });

        preference = findPreference(PrefUtils.DECSYNC_DIRECTORY);
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), FilePickerActivity.class);
                intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
                intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
                intent.putExtra(FilePickerActivity.EXTRA_START_PATH, PrefUtils.getString(PrefUtils.DECSYNC_DIRECTORY, getDefaultDecsyncBaseDir()));
                startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY);
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        // The ringtone summary text should be updated using
        // OnSharedPreferenceChangeListener(), but I can't get it to work.
        // Updating in onResume is a very simple hack that seems to work, but is inefficient.
        setRingtoneSummary();

        super.onResume();

    }

    private void setRingtoneSummary() {
        Preference ringtone_preference = findPreference(PrefUtils.NOTIFICATIONS_RINGTONE);
        Uri ringtoneUri = Uri.parse(PrefUtils.getString(PrefUtils.NOTIFICATIONS_RINGTONE, ""));
        if (TextUtils.isEmpty(ringtoneUri.toString())) {
            ringtone_preference.setSummary(R.string.settings_notifications_ringtone_none);
        } else {
            Ringtone ringtone = RingtoneManager.getRingtone(MainApplication.getContext(), ringtoneUri);
            if (ringtone == null) {
                ringtone_preference.setSummary(R.string.settings_notifications_ringtone_none);
            } else {
                ringtone_preference.setSummary(ringtone.getTitle(MainApplication.getContext()));
            }
        }
    }

    private void setDecsyncDirSummary() {
        Preference preference = findPreference(PrefUtils.DECSYNC_DIRECTORY);
        String dir = PrefUtils.getString(PrefUtils.DECSYNC_DIRECTORY, getDefaultDecsyncBaseDir());
        preference.setSummary(dir);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CHOOSE_DECSYNC_DIRECTORY) {
            Uri uri = data == null ? null : data.getData();
            if (resultCode == Activity.RESULT_OK && uri != null) {
                String oldDir = PrefUtils.getString(PrefUtils.DECSYNC_DIRECTORY, getDefaultDecsyncBaseDir());
                String newDir = Utils.getFileForUri(uri).getPath();
                if (!oldDir.equals(newDir)) {
                    PrefUtils.putString(PrefUtils.DECSYNC_DIRECTORY, newDir);
                    setDecsyncDirSummary();
                    DecsyncUtils.INSTANCE.directoryChanged(getActivity());
                }
            }
        }
    }
}
