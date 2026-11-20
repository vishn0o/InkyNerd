package com.wikiink.app.data.quiz

/**
 * A quiz topic. [category] is the English Wikipedia category its questions are drawn from
 * (searched with `deepcat:`, so sub-categories count); null means "anything" (Auto).
 * Every node is playable on its own, and most have [children] to go deeper.
 */
data class QuizTopic(
    val id: String,
    val label: String,
    val category: String?,
    val children: List<QuizTopic> = emptyList(),
)

private fun t(id: String, label: String, category: String = label, vararg children: QuizTopic) =
    QuizTopic(id, label, category, children.toList())

/** The topic tree: broad subjects first, each drilling down (e.g. Sports → Motorsport → Formula One). */
object QuizTopics {

    val AUTO = QuizTopic(
        "auto", "Auto (General)", null,
        listOf(
            t(
                "science", "Science", "Science",
                t("physics", "Physics", "Physics",
                    t("astrophysics", "Astrophysics"),
                    t("quantum", "Quantum mechanics"),
                    t("particles", "Particle physics"),
                    t("relativity", "Relativity", "Theory of relativity"),
                ),
                t("chemistry", "Chemistry", "Chemistry",
                    t("elements", "Chemical elements"),
                    t("organic", "Organic chemistry"),
                ),
                t("biology", "Biology", "Biology",
                    t("genetics", "Genetics"),
                    t("evolution", "Evolution", "Evolutionary biology"),
                    t("microbes", "Microbiology"),
                ),
                t("earthsci", "Earth science", "Earth sciences",
                    t("volcanoes", "Volcanology"),
                    t("weather", "Meteorology"),
                    t("geology", "Geology"),
                ),
                t("medicine", "Medicine", "Medicine",
                    t("anatomy", "Human anatomy"),
                    t("diseases", "Diseases", "Diseases and disorders"),
                ),
            ),
            t(
                "space", "Space", "Outer space",
                t("planets", "Planets"),
                t("exoplanets", "Exoplanets"),
                t("stars", "Stars"),
                t("blackholes", "Black holes"),
                t("moons", "Moons", "Natural satellites"),
                t("comets", "Comets"),
                t("spaceflight", "Spaceflight"),
            ),
            t(
                "maths", "Mathematics", "Mathematics",
                t("numbers", "Number theory"),
                t("geometry", "Geometry"),
                t("paradoxes", "Paradoxes"),
            ),
            t(
                "tech", "Technology", "Technology",
                t("computing", "Computing", "Computing",
                    t("ai", "Artificial intelligence"),
                    t("proglangs", "Programming languages"),
                    t("crypto", "Cryptography"),
                    t("security", "Computer security"),
                ),
                t("videogames", "Video games"),
                t("inventions", "Inventions"),
                t("internet", "Internet"),
                t("aviation", "Aviation"),
                t("robots", "Robotics"),
            ),
            t(
                "history", "History", "History",
                t("egypt", "Ancient Egypt"),
                t("greece", "Ancient Greece"),
                t("rome", "Ancient Rome"),
                t("medieval", "Middle Ages"),
                t("vikings", "Vikings"),
                t("ww2", "World War II"),
                t("renaissance", "Renaissance"),
                t("industrial", "Industrial Revolution"),
                t("coldwar", "Cold War"),
                t("spacerace", "Space Race"),
            ),
            t(
                "geography", "Geography", "Geography",
                t("countries", "Countries"),
                t("islands", "Islands"),
                t("rivers", "Rivers"),
                t("mountains", "Mountains"),
                t("cities", "Cities"),
                t("deserts", "Deserts"),
                t("lakes", "Lakes"),
            ),
            t(
                "sports", "Sports", "Sports",
                t("motorsport", "Motorsport", "Motorsport",
                    t("f1", "Formula One"),
                    t("motogp", "MotoGP", "Grand Prix motorcycle racing"),
                    t("rally", "Rallying"),
                    t("nascar", "NASCAR"),
                ),
                t("cricket", "Cricket"),
                t("football", "Football", "Association football"),
                t("tennis", "Tennis"),
                t("basketball", "Basketball"),
                t("olympics", "Olympics", "Olympic Games"),
                t("chess", "Chess"),
                t("boardgames", "Board games"),
            ),
            t(
                "nature", "Nature", "Nature",
                t("animals", "Animals", "Animals",
                    t("dinosaurs", "Dinosaurs"),
                    t("birds", "Birds"),
                    t("insects", "Insects"),
                    t("mammals", "Mammals"),
                    t("reptiles", "Reptiles"),
                    t("sharks", "Sharks"),
                    t("sealife", "Marine life", "Marine biology"),
                ),
                t("plants", "Plants"),
                t("fungi", "Fungi"),
            ),
            t(
                "food", "Food & drink", "Food and drink",
                t("cuisine", "Cuisines", "Cuisine"),
                t("cheese", "Cheese", "Cheeses"),
                t("desserts", "Desserts"),
                t("beer", "Beer"),
                t("cocktails", "Cocktails"),
                t("spices", "Spices"),
                t("coffee", "Coffee"),
                t("tea", "Tea"),
            ),
            t(
                "culture", "Arts & culture", "Culture",
                t("music", "Music", "Music",
                    t("classical", "Classical music"),
                    t("jazz", "Jazz"),
                    t("rock", "Rock music"),
                ),
                t("film", "Film"),
                t("tv", "Television"),
                t("anime", "Anime"),
                t("comics", "Comics"),
                t("fashion", "Fashion"),
                t("photography", "Photography"),
                t("literature", "Literature"),
                t("art", "Art", "Art",
                    t("paintings", "Paintings"),
                ),
                t("mythology", "Mythology", "Mythology",
                    t("greekmyth", "Greek mythology"),
                    t("norsemyth", "Norse mythology"),
                ),
            ),
            t(
                "language", "Language", "Language",
                t("etymology", "Etymology"),
                t("writing", "Writing systems"),
                t("conlangs", "Constructed languages"),
                t("idioms", "Idioms"),
            ),
            t(
                "transport", "Transport", "Transport",
                t("rail", "Railways", "Rail transport"),
                t("ships", "Ships"),
                t("cars", "Cars", "Automobiles"),
                t("aircraft", "Aircraft"),
            ),
            t(
                "military", "Military", "Military",
                t("battles", "Battles"),
                t("weapons", "Weapons"),
                t("navies", "Navies"),
                t("espionage", "Espionage"),
            ),
            t(
                "architecture", "Architecture", "Architecture",
                t("skyscrapers", "Skyscrapers"),
                t("castles", "Castles"),
                t("bridges", "Bridges"),
                t("lighthouses", "Lighthouses"),
            ),
            t(
                "economics", "Economics & business", "Economics",
                t("companies", "Companies"),
                t("currencies", "Currencies"),
                t("brands", "Brands"),
            ),
            t(
                "politics", "Politics & law", "Politics",
                t("law", "Law"),
                t("elections", "Elections"),
                t("flags", "Flags"),
                t("micronations", "Micronations"),
            ),
            t(
                "philosophy", "Religion & philosophy", "Philosophy",
                t("philo", "Philosophy"),
                t("religion", "Religion"),
                t("saints", "Saints"),
                t("logic", "Logic"),
            ),
            t(
                "psychology", "Psychology", "Psychology",
                t("biases", "Cognitive biases"),
                t("memory", "Memory"),
                t("sleep", "Sleep"),
            ),
            t(
                "oddities", "Mysteries & oddities", "Unsolved problems",
                t("unsolved", "Unsolved problems"),
                t("hoaxes", "Hoaxes"),
                t("cryptids", "Cryptids"),
                t("conspiracies", "Conspiracy theories"),
            ),
        ),
    )

    private val byId: Map<String, QuizTopic> = buildMap { walk(AUTO) { put(it.id, it) } }
    private val parents: Map<String, QuizTopic> = buildMap {
        walk(AUTO) { node -> node.children.forEach { put(it.id, node) } }
    }

    private fun walk(node: QuizTopic, visit: (QuizTopic) -> Unit) {
        visit(node)
        node.children.forEach { walk(it, visit) }
    }

    fun find(id: String?): QuizTopic = id?.let { byId[it] } ?: AUTO

    fun parent(topic: QuizTopic): QuizTopic? = parents[topic.id]

    /** Root-to-[topic] chain, for breadcrumbs: Auto › Sports › Motorsport › Formula One. */
    fun path(topic: QuizTopic): List<QuizTopic> =
        generateSequence(topic) { parents[it.id] }.toList().reversed()

    val all: Collection<QuizTopic> get() = byId.values

    /**
     * The Wikipedia category to draw one batch of questions from. A broad topic mixes in its
     * branches (picking a random node of its subtree), which gives variety and avoids searching
     * one enormous category tree. Null = anything (Auto).
     */
    fun drawCategory(topic: QuizTopic, pick: (Int) -> Int): String? {
        if (topic.category == null) return null
        val subtree = buildList { walk(topic) { add(it) } }
        return subtree[pick(subtree.size)].category
    }
}
