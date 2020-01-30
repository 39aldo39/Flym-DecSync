/**
 * spaRSS DecSync
 * <p/>
 * Copyright (c) 2018 Aldo Gunsing
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

package org.decsync.sparss.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.decsync.library.Decsync
import org.decsync.sparss.utils.DecsyncUtils.getDecsync
import org.decsync.sparss.utils.Extra

@ExperimentalStdlibApi
class DecsyncService : Service() {

    private var mDecsync: Decsync<Extra>? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (mDecsync == null) {
            mDecsync = getDecsync()
        }
        return START_STICKY
    }
}
