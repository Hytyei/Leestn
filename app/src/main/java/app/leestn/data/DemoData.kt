package app.leestn.data

private class RawEp(val title: String, val min: Int, val date: String, val blurb: String, val after: String? = null)
private class RawCat(val id: String, val name: String, val short: String, val kind: Kind, val eps: List<RawEp>)

private fun e(t: String, m: Int, d: String, b: String, after: String? = null) = RawEp(t, m, d, b, after)
private fun mainCat(id: String, name: String, short: String, eps: List<RawEp>) = RawCat(id, name, short, Kind.MAIN, eps)
private fun sideCat(id: String, name: String, short: String, eps: List<RawEp>) = RawCat(id, name, short, Kind.SIDE, eps)

private fun makeShow(
    id: String, name: String, host: String, genre: String, motif: Motif, hueA: Float, hueB: Float,
    tagline: String, @Suppress("UNUSED_PARAMETER") tx: List<Pair<String, String>>, cats: List<RawCat>,
): Podcast {
    val categories = cats.map { Category(it.id, it.name, it.short, it.kind) }
    val eps = ArrayList<Episode>()
    cats.forEachIndexed { ci, c ->
        c.eps.forEachIndexed { i, r ->
            eps.add(
                Episode(
                    id = "$id-${c.id}e${i + 1}", pid = id, cat = categories[ci], num = i + 1, title = r.title,
                    durationSec = r.min * 60, date = r.date, blurb = r.blurb, description = r.blurb,
                    afterId = r.after?.let { "$id-$it" }, audioUrl = "",
                )
            )
        }
    }
    return Podcast(id = id, name = name, host = host, genre = genre, tagline = tagline, eps = eps, cats = categories, hueA = hueA, hueB = hueB, motif = motif)
}

/**
 * Fictional shows used only to generate the demo friends' activity. They have no audio and
 * are never shown as playable content.
 */
object DemoData {
    val podcasts: List<Podcast> = listOf(
        makeShow(
            "lsg", "The Long Signal", "Written by Mara Ilves", "Fiction", Motif.ARCS, 196f, 262f,
            "A sci-fi audio drama about a radio telescope crew that hears something answer back.",
            listOf(
                "Okafor" to "Array status is nominal. Which is exactly what bothers me.",
                "Ilves" to "Run the capture again. Same window, same gain, nothing changed.",
                "Okafor" to "It repeats every eleven minutes. Noise does not keep a schedule.",
                "Ilves" to "Then call it what it is. Something out there is keeping time.",
                "Okafor" to "Log it. Do not tell the valley station until we are sure.",
                "Ilves" to "Sure of what? That we heard it, or that it heard us?",
                "Okafor" to "Turn the dish back to the quiet part of the sky.",
                "Ilves" to "There is no quiet part anymore. Listen.",
            ),
            listOf(
                mainCat("s1", "Season 1", "S1", listOf(
                    e("Static", 34, "2025-09-02", "A night shift at Halden Array goes quiet."),
                    e("Carrier Wave", 38, "2025-09-09", "The crew finds a pattern inside the noise."),
                    e("Handshake", 41, "2025-09-16", "Someone replies, and nobody agrees who."),
                    e("The Dead Hour", 36, "2025-09-23", "Power fails. The signal does not."),
                    e("Origin Point", 47, "2025-09-30", "A coordinate points back at the station."),
                )),
                mainCat("s2", "Season 2", "S2", listOf(
                    e("Reacquisition", 39, "2026-03-03", "Six months later, the array wakes on its own."),
                    e("Sidereal", 42, "2026-03-10", "A second listening post goes dark."),
                    e("Long Baseline", 44, "2026-03-17", "Two stations compare notes and the dates disagree."),
                    e("Open Channel", 52, "2026-03-24", "The crew answers back."),
                )),
                sideCat("ex", "Extras", "EX", listOf(
                    e("Field Recording: The Dish at 4 a.m.", 12, "2025-10-14", "The real sound of a radio dish, no drama.", "s1e3"),
                    e("Interview: Writing the Silence", 29, "2025-11-04", "Mara Ilves on writing a story made of gaps.", "s1e5"),
                    e("Cast Table Read: Season 2", 24, "2026-02-17", "The cast reacts before the new season.", "s1e5"),
                    e("Commentary: Handshake", 33, "2026-05-12", "Line-by-line notes on the pivotal episode.", "s1e3"),
                )),
            ),
        ),
        makeShow(
            "smg", "Salt & Margin", "Hosted by Priya Raman", "Business", Motif.BARS, 18f, 330f,
            "Business stories about the small numbers that quietly run the world.",
            listOf(
                "Priya" to "The ledger looked fine until you read the footnotes, and nobody reads footnotes.",
                "Daniel" to "Forty crates went in and thirty-one came out. Where are the other nine?",
                "Priya" to "That is the whole story. Margins are made of small disappearances.",
                "Daniel" to "So the owner knew? Or the spreadsheet knew, and the owner trusted it?",
                "Priya" to "Those are the same thing if you never check the spreadsheet.",
                "Daniel" to "Walk me through the money, slowly, like I am a regulator.",
                "Priya" to "A dollar comes in, eighty cents leaves before lunch, the rest is optimism.",
                "Daniel" to "And that optimism has a payroll.",
            ),
            listOf(
                mainCat("s1", "Season 1", "S1", listOf(
                    e("The Salt Cartel", 31, "2025-10-07", "How a mineral shaped a century of trade."),
                    e("Margin Call at Dawn", 28, "2025-10-14", "A small bakery chain and one wrong spreadsheet."),
                    e("Ghost Inventory", 35, "2025-10-21", "Where the missing stock actually went."),
                    e("The Last Mile", 33, "2025-10-28", "Why delivery costs more than the product."),
                    e("Paper Profits", 40, "2025-11-04", "A finale about numbers that lie politely."),
                )),
                mainCat("s2", "Season 2", "S2", listOf(
                    e("Price Tag", 30, "2026-04-07", "Who really decides what a sandwich costs."),
                    e("The Quiet Merger", 37, "2026-04-14", "Two rivals, one handshake, no press release."),
                    e("Dead Stock", 32, "2026-04-21", "The warehouse nobody wants to open."),
                )),
                sideCat("ex", "Extras", "EX", listOf(
                    e("Listener Mailbag", 22, "2025-12-02", "Your Season 1 questions, answered.", "s1e5"),
                    e("Spreadsheet Walkthrough", 18, "2026-01-13", "The actual sheet from Margin Call at Dawn.", "s1e2"),
                )),
            ),
        ),
        makeShow(
            "smh", "Small Hours", "Hosted by Jonas & Leilani", "Culture", Motif.MOON, 255f, 310f,
            "Late-night phone calls with strangers who cannot sleep.",
            listOf(
                "Jonas" to "Okay, we are live, and it is officially too late to be reasonable.",
                "Leilani" to "Tonight’s caller says she has been awake since Tuesday and feels great.",
                "Jonas" to "That is the least reassuring sentence I have heard this week.",
                "Leilani" to "She wants to talk about the neighbor who plays piano at midnight.",
                "Jonas" to "Honestly the piano is the best part of the building.",
                "Leilani" to "You say that, but you do not live under it.",
                "Jonas" to "Put her through. We have until the coffee runs out.",
                "Leilani" to "Hello, you are on Small Hours. Tell us what kept you up.",
            ),
            listOf(
                mainCat("s1", "Season 1", "S1", listOf(
                    e("Can’t Sleep", 44, "2025-08-05", "Two strangers, one phone line, 3 a.m."),
                    e("The Neighbor Upstairs", 51, "2025-08-12", "A noisy floor becomes a long conversation."),
                    e("Night Bus", 39, "2025-08-19", "Stories from the last route home."),
                    e("Insomniac’s Cookbook", 47, "2025-08-26", "What people actually eat at 2 a.m."),
                    e("Sunrise Shift", 56, "2025-09-02", "Bakers, nurses and the people who open first."),
                )),
                mainCat("s2", "Season 2", "S2", listOf(
                    e("Daylight Saving", 46, "2026-07-07", "A new season about time itself."),
                    e("Clocks Back", 49, "2026-07-14", "The hour we get, and what we do with it."),
                )),
                sideCat("lv", "Live Shows", "LV", listOf(
                    e("Live in Rotterdam", 68, "2026-01-20", "A full night show with a studio audience.", "s1e3"),
                    e("Live in Lisbon", 72, "2026-06-16", "The second live recording, with new guests.", "s1e5"),
                )),
            ),
        ),
        makeShow(
            "cop", "Cold Open", "Hosted by Elena Marsh", "True Crime", Motif.BEAM, 348f, 255f,
            "Verified, slow-built investigations that start in the middle of the story.",
            listOf(
                "Elena" to "The timeline everyone agreed on had one problem: it needed two clocks to be right.",
                "Reyes" to "I wrote down the time because dispatch told me to. Eleven forty, give or take.",
                "Elena" to "Give or take is doing a lot of work in that sentence.",
                "Reyes" to "We secured the door, we waited, and then the hallway light went out.",
                "Elena" to "The records show the building’s power never failed that night.",
                "Reyes" to "Then somebody turned it off by hand. I never said it failed.",
                "Elena" to "We went back to the original recording and listened to the silence.",
                "Reyes" to "Do not trust any version of this that sounds too clean.",
            ),
            listOf(
                mainCat("s1", "Season 1", "S1", listOf(
                    e("The Call", 42, "2025-06-03", "A 911 call that ends in the middle of a word."),
                    e("The First Officer", 46, "2025-06-10", "What the officer on scene remembers."),
                    e("The Neighbor’s Timeline", 44, "2025-06-17", "Three clocks, three different nights."),
                    e("The Lab", 50, "2025-06-24", "A sample that sat untested for nine years."),
                    e("The Verdict", 58, "2025-07-01", "Twelve people, four days, one decision."),
                )),
                mainCat("s2", "Season 2", "S2", listOf(
                    e("Cold Storage", 41, "2026-02-03", "A new case begins in a rented unit."),
                    e("The Ledger", 45, "2026-02-10", "A bookkeeper keeps the only record."),
                    e("The Witness", 48, "2026-02-17", "Someone saw everything and said nothing."),
                    e("Exhibit C", 55, "2026-02-24", "What the jury never got to see."),
                )),
                sideCat("cf", "Case Files", "CF", listOf(
                    e("Case File: The Harlow Timeline", 16, "2025-07-15", "A plain-language timeline of Season 1.", "s1e5"),
                    e("Q&A with the Producer", 27, "2025-09-09", "How the team verifies every claim.", "s1e5"),
                    e("Case File: Storage Unit 114", 19, "2026-03-10", "Documents behind the Season 2 opener.", "s2e1"),
                )),
            ),
        ),
        makeShow(
            "fnn", "Field Notes from Nowhere", "Hosted by Ibrahim Oyelaran", "Travel", Motif.WAVES, 150f, 200f,
            "Slow travel audio from places at the end of the road.",
            listOf(
                "Ibrahim" to "The road stops being a road about four miles back. Now it is a suggestion.",
                "Tove" to "The café opens when the owner wakes up, which is a kind of schedule.",
                "Ibrahim" to "Population forty-one, if you count the dog, and everyone does.",
                "Tove" to "Wind comes first, then the light, then someone puts a kettle on.",
                "Ibrahim" to "We have not seen another car since the ferry. It feels unreasonably good.",
                "Tove" to "There is a map in the café window, and every pin is a story.",
                "Ibrahim" to "Pick one and I will tell you why we should not go there.",
                "Tove" to "That one. The one with the lighthouse.",
            ),
            listOf(
                mainCat("s1", "Season 1", "S1", listOf(
                    e("The Road to Nowhere, Population 41", 37, "2025-11-11", "A village at the end of a gravel road."),
                    e("Salt Flats at Dawn", 29, "2025-11-18", "Sixty miles of white and one café."),
                    e("The Last Ferry", 33, "2025-11-25", "An island that closes for winter."),
                    e("Rain Shadow", 35, "2025-12-02", "Following a mountain’s weather across a border."),
                    e("Lighthouse Keepers", 43, "2025-12-09", "Three people who still live inside the beam."),
                )),
                sideCat("ex", "Extras", "EX", listOf(
                    e("Unedited: Wind on the Flats", 9, "2025-12-23", "Nine minutes of raw field audio.", "s1e2"),
                    e("Packing List & Maps", 14, "2026-01-06", "The gear and routes behind the season.", "s1e5"),
                )),
            ),
        ),
        makeShow(
            "ps", "Press Start", "Hosted by Dana & Kit", "Games", Motif.PIXELS, 174f, 300f,
            "The strange, human history of video games, one cartridge at a time.",
            listOf(
                "Dana" to "Everyone remembers the console, but the real story is the cartridge.",
                "Kit" to "A plastic shell with a chip and a prayer, shipped to ten million bedrooms.",
                "Dana" to "And blowing into it did nothing. We need to say that on the record.",
                "Kit" to "Do not tell the nostalgia crowd. They have rituals.",
                "Dana" to "The arcade owners saw it coming before the publishers did.",
                "Kit" to "Quarters were the first analytics. A heavy machine meant a good game.",
                "Dana" to "So who decided a game should end, and who decided it should not?",
                "Kit" to "That is next episode, and it is a fight.",
            ),
            listOf(
                mainCat("s1", "Season 1", "S1", listOf(
                    e("The Cartridge Years", 45, "2025-10-02", "How a whole industry fit in a plastic shell."),
                    e("Arcade Economics", 41, "2025-10-09", "Quarters, queues and the first high scores."),
                    e("The Great Crash", 52, "2025-10-16", "Full shelves and nobody buying."),
                    e("Polygons", 48, "2025-10-23", "The leap to three dimensions, awkwardly."),
                    e("Online At Last", 50, "2025-10-30", "Dial-up lobbies and the first guilds."),
                    e("Save Points", 57, "2025-11-06", "Why games stopped ending."),
                )),
                sideCat("ex", "Extras", "EX", listOf(
                    e("Cartridge Repair Demo", 15, "2025-11-20", "Fixing a dead cartridge, with parts list.", "s1e1"),
                    e("Listener Top 10", 31, "2026-01-27", "Your favorite games, ranked live.", "s1e6"),
                )),
            ),
        ),
    )

    val shows: Map<String, Podcast> = podcasts.associateBy { it.id }
    val allEpisodes: List<Episode> = podcasts.flatMap { it.eps }
    val episodes: Map<String, Episode> = allEpisodes.associateBy { it.id }

    fun show(id: String): Podcast? = shows[id]
    fun episode(id: String): Episode? = episodes[id]
    fun isDemo(id: String): Boolean = shows.containsKey(id) || episodes.containsKey(id)
}
