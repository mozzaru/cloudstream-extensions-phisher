package com.Anichin

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.Okrulink

@CloudstreamPlugin
class AnimexinProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Anichin())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Okrulink())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(RpmShare())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(NewPlayer())
        registerExtractorAPI(VidGuard())
    }
}
