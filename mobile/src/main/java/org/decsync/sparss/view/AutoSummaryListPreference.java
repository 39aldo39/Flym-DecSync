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
 */

package org.decsync.sparss.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

public class AutoSummaryListPreference extends ListPreference {

    public AutoSummaryListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        CharSequence entry = getEntry();
        CharSequence summary = super.getSummary();
        if (summary == null || entry == null) {
            return null;
        }
        return String.format(summary.toString(), entry);
    }

    @Override
    public void setValue(final String value) {
        super.setValue(value);
        notifyChanged();
    }
}