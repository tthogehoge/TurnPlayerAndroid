import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class PodcastEpisode(
    val title: String,
    val description: String,
    val pubDate: String,
    val duration: String,
    val url: String
)

class RssParser {
    fun parse(rssContent: String): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        var currentEpisode: MutableMap<String, String> = mutableMapOf()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(rssContent))

            var eventType = parser.eventType
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "item" -> {
                                insideItem = true
                                currentEpisode = mutableMapOf()
                            }
                            "title" -> {
                                if (insideItem) {
                                    parser.next()
                                    currentEpisode["title"] = parser.text ?: ""
                                }
                            }
                            "description" -> {
                                if (insideItem) {
                                    parser.next()
                                    currentEpisode["description"] = parser.text ?: ""
                                }
                            }
                            "pubDate" -> {
                                if (insideItem) {
                                    parser.next()
                                    currentEpisode["pubDate"] = parser.text ?: ""
                                }
                            }
                            "duration" -> {
                                if (insideItem) {
                                    parser.next()
                                    currentEpisode["duration"] = parser.text ?: ""
                                }
                            }
                            "enclosure" -> {
                                if (insideItem) {
                                    currentEpisode["url"] = parser.getAttributeValue(null, "url") ?: ""
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            insideItem = false
                            episodes.add(
                                PodcastEpisode(
                                    title = currentEpisode["title"] ?: "",
                                    description = currentEpisode["description"] ?: "",
                                    pubDate = currentEpisode["pubDate"] ?: "",
                                    duration = currentEpisode["duration"] ?: "",
                                    url = currentEpisode["url"] ?: ""
                                )
                            )
                        }
                    }
                }
                eventType = parser.next()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return episodes
    }
}