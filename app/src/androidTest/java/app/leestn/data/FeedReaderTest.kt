package app.leestn.data

import junit.framework.TestCase

/** Runs on a device because the bug lived in Android's own XML parser, which a plain JVM test does not have. */
class FeedReaderTest : TestCase() {
    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:podcast="https://podcastindex.org/namespace/1.0" version="2.0">
          <channel>
            <title><![CDATA[Tales Behind The Pixels]]></title>
            <description>Short</description>
            <itunes:author>Hytyei</itunes:author>
            <itunes:image href="https://cdn.example.com/cover.jpg"/>
            <itunes:category text="TV &amp; Film"/>
            <language>en</language>
            <item>
              <title>Episode one</title>
              <itunes:title>Episode one, the long name</itunes:title>
              <guid>abc</guid>
              <pubDate>Tue, 03 Nov 2026 01:47:58 GMT</pubDate>
              <description>Plain</description>
              <content:encoded><![CDATA[<p>The much longer show notes</p>]]></content:encoded>
              <enclosure url="https://cdn.example.com/1.mp3" type="audio/mpeg"/>
              <itunes:image href="https://cdn.example.com/ep1.jpg"/>
              <itunes:duration>00:42:10</itunes:duration>
              <itunes:season>2</itunes:season>
              <itunes:episode>5</itunes:episode>
              <itunes:episodeType>bonus</itunes:episodeType>
              <podcast:transcript url="https://cdn.example.com/1.vtt" type="text/vtt"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private fun read(xml: String) = FeedReader.read(xml.byteInputStream(), "https://anchor.fm/s/1/podcast/rss")

    fun testChannelArtworkFromItunesImage() = assertEquals("https://cdn.example.com/cover.jpg", read(feed).imageUrl)

    fun testChannelAuthorAndGenre() {
        val p = read(feed)
        assertEquals("Hytyei", p.author)
        assertEquals("TV & Film", p.genre)
    }

    fun testItemFieldsInPrefixedTags() {
        val i = read(feed).items.single()
        assertEquals("Episode one", i.title)
        assertEquals(2530, i.durationSec)
        assertEquals(2, i.season)
        assertEquals(5, i.episode)
        assertEquals("bonus", i.type)
        assertEquals("https://cdn.example.com/ep1.jpg", i.imageUrl)
        assertEquals("<p>The much longer show notes</p>", i.descHtml)
        assertEquals(listOf("https://cdn.example.com/1.vtt" to "text/vtt"), i.transcripts)
    }

    fun testFeedThatForgotToDeclareItsPrefixStillReads() {
        val p = read(feed.replace(""" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"""", ""))
        assertEquals("https://cdn.example.com/cover.jpg", p.imageUrl)
    }

    fun testPlainImageTagStillWorks() {
        val xml = """<rss version="2.0"><channel><title>T</title><image><url>https://cdn.example.com/plain.png</url><title>Logo</title></image></channel></rss>"""
        val p = read(xml)
        assertEquals("https://cdn.example.com/plain.png", p.imageUrl)
        assertEquals("T", p.title)
    }
}
