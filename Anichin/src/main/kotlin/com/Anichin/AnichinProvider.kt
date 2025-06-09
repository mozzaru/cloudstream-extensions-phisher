package com.Anichin

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Dailymotion
//import com.lagradost.cloudstream3.extractors.Odnoklassniki
//import com.lagradost.cloudstream3.extractors.OkRuHTTP
//import com.lagradost.cloudstream3.extractors.OkRuSSL
//import com.lagradost.cloudstream3.extractors.Okrulink
import com.Anichin.extractors.*
import com.Anichin.extractors.Okrunew
import com.Anichin.extractors.Rumble
import com.Anichin.extractors.StreamRuby
import com.Anichin.extractors.VidGuard


@CloudstreamPlugin
class AnichinProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Anichin())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Okrunew())
        //registerExtractorAPI(OkRuSSL())
        //registerExtractorAPI(OkRuHTTP())
        //registerExtractorAPI(Odnoklassniki())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(VidGuard())
    }
}
