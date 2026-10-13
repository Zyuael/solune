// SPDX-FileCopyrightText: 2026 ZYUAEL
// SPDX-License-Identifier: GPL-3.0-or-later

package com.zyuael.solune

import android.app.Application
import com.zyuael.solune.data.Downloads
import com.zyuael.solune.data.Http
import com.zyuael.solune.data.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class SoluneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        NewPipe.init(OkHttpDownloader(Http.client), Localization("en", "US"), ContentCountry(country))
        Downloads.init(this)
    }
}
