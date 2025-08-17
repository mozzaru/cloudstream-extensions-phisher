package com.donghub

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DonghubProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Donghub())
        registerMainAPI(ArchiveOrgExtractor())
    }
}
