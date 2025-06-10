package com.Anichin

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.Anichin.extractors.*
import com.Anichin.extractors.OkruExtractor
import com.Anichin.extractors.Rumble
import com.Anichin.extractors.StreamRuby1
import com.Anichin.extractors.StreamRuby2
import com.Anichin.extractors.VidGuard
//import com.lagradost.cloudstream3.extractors.Odnoklassniki
//import com.lagradost.cloudstream3.extractors.OkRuHTTP
//import com.lagradost.cloudstream3.extractors.OkRuSSL
//import com.lagradost.cloudstream3.extractors.Okrulink


@CloudstreamPlugin
class AnichinProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Anichin())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(OkruExtractor())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(VidGuard())
    }

    override fun getExtractorList(): List<ExtractorApi> {
        return listOf(
            StreamRuby1(),
            StreamRuby2()
        )
    }
}