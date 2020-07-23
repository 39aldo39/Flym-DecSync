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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import org.decsync.library.DecsyncPrefUtils;
import org.decsync.sparss.MainApplication;
import org.decsync.sparss.R;
import org.decsync.sparss.service.RefreshService;
import org.decsync.sparss.utils.DecsyncUtils;
import org.decsync.sparss.utils.PrefUtils;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import static org.decsync.sparss.utils.DecsyncUtilsKt.getDefaultDecsyncDir;

public class GeneralPrefsFragment extends PreferenceFragmentCompat {

    private static final int CHOOSE_DECSYNC_FILE = 0;
    private static final int PERMISSIONS_REQUEST_DECSYNC = 2;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.general_preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_USE_SAF, false)) {
            preference = findPreference(PrefUtils.DECSYNC_ENABLED);
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Activity activity = getActivity();
                    if (activity == null) {
                        return false;
                    }
                    if (Boolean.TRUE.equals(newValue)) {
                        DecsyncPrefUtils.INSTANCE.chooseDecsyncDir(GeneralPrefsFragment.this);
                        return false;
                    }
                    return true;
                }
            });

            preference = findPreference(PrefUtils.DECSYNC_FILE);
            preference.setVisible(false);

            preference = findPreference(PrefUtils.DECSYNC_FILE_RESET);
            preference.setVisible(false);
        } else {
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
                            return true;
                        } else {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_DECSYNC);
                            return false;
                        }
                    }
                    return true;
                }
            });

            preference = findPreference(PrefUtils.DECSYNC_FILE);
            preference.setSummary(PrefUtils.getString(PrefUtils.DECSYNC_FILE, getDefaultDecsyncDir()));
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(requireActivity(), FilePickerActivity.class);
                    intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
                    intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
                    intent.putExtra(FilePickerActivity.EXTRA_START_PATH, PrefUtils.getString(PrefUtils.DECSYNC_FILE, getDefaultDecsyncDir()));
                    startActivityForResult(intent, CHOOSE_DECSYNC_FILE);
                    return true;
                }
            });

            preference = findPreference(PrefUtils.DECSYNC_FILE_RESET);
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.settings_decsync_file_reset)
                            .setMessage(getString(R.string.settings_decsync_file_reset_message, getDefaultDecsyncDir()))
                            .setNegativeButton(android.R.string.no, null)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    setDecsyncFile(getDefaultDecsyncDir());
                                }
                            })
                            .show();
                    return true;
                }
            });
        }
    }

    private void setDecsyncFile(String dir) {
        PrefUtils.putString(PrefUtils.DECSYNC_FILE, dir);
        findPreference(PrefUtils.DECSYNC_FILE).setSummary(dir);
        DecsyncUtils.INSTANCE.initSync(requireActivity());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_USE_SAF, false)) {
            Activity activity = requireActivity();
            DecsyncPrefUtils.INSTANCE.chooseDecsyncDirResult(activity, requestCode, resultCode, data, new Function1<Uri, Unit>() {
                @Override
                public Unit invoke(Uri uri) {
                    PrefUtils.putBoolean(PrefUtils.DECSYNC_ENABLED, true);
                    CheckBoxPreference preference = findPreference(PrefUtils.DECSYNC_ENABLED);
                    if (preference != null) {
                        preference.setChecked(true);
                    }
                    DecsyncUtils.INSTANCE.initSync(activity);
                    return Unit.INSTANCE;
                }
            });
        } else {
            if (requestCode == CHOOSE_DECSYNC_FILE) {
                Uri uri = data == null ? null : data.getData();
                if (resultCode == Activity.RESULT_OK && uri != null) {
                    String oldDir = PrefUtils.getString(PrefUtils.DECSYNC_FILE, getDefaultDecsyncDir());
                    String newDir = Utils.getFileForUri(uri).getPath();
                    if (!oldDir.equals(newDir)) {
                        setDecsyncFile(newDir);
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        switch (requestCode)
        {
            case PERMISSIONS_REQUEST_DECSYNC:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    DecsyncUtils.INSTANCE.initSync(requireActivity());
                    CheckBoxPreference preference = findPreference(PrefUtils.DECSYNC_ENABLED);
                    preference.setChecked(true);
                }
        }
    }
}
