package com.wikiink.app.data.repository

/**
 * Subject taxonomy for facts — "anything under the moon". Each fact is classified from its
 * Wikipedia short description (most reliable, e.g. "Species of deep-sea anglerfish"), falling back
 * to its title + hook. Order matters: more specific subjects are checked first.
 *
 * PLANTS is checked before ANIMALS so the generic "species"/"genus" keywords in ANIMALS only catch
 * non-plant taxa. Keywords match at the start of a word, so stems work ("astronom" → astronomy, astronomer) and
 * "art" never fires inside "start". A trailing `\b` in a keyword makes it a whole word.
 */
enum class Subject(val label: String, val chip: String, keywords: List<String>) {
    GAMES("Video games", "GAMES", listOf("video game", "videogame", "game developer", "nintendo", "playstation", "xbox", "arcade game", "esports", "speedrun")),
    FOSSILS("Fossils & dinosaurs", "FOSSILS", listOf("dinosaur", "fossil", "palaeont", "paleont", "extinct", "prehistoric", "pterosaur", "trilobite", "mammoth", "ichthyosaur", "plesiosaur")),
    SPACE("Space", "SPACE", listOf("galax", "planet", "asteroid", "comet", "nebula", "exoplanet", "moon\\b", "moons\\b", "astronom", "astrophys", "spacecraft", "space probe", "space station", "spaceflight", "satellite", "telescope", "constellation", "meteor", "astronaut", "cosmonaut", "nasa\\b", "orbit", "solar system", "black hole", "supernova", "binary star", "neutron star", "variable star", "star cluster", "dwarf star", "pulsar", "quasar")),
    MEDICINE("Body & medicine", "MEDICINE", listOf("disease", "medic", "syndrome", "anatom", "surgeon", "surgery", "physician", "vaccin", "virus", "bacteri", "hospital", "disorder", "pharma", "nurse", "patholog", "parasit", "infection", "epidemic", "pandemic", "dentist", "organ\\b", "blood", "brain", "psychiatr", "psycholog")),
    FOOD("Food & drink", "FOOD", listOf("dish\\b", "dishes", "food", "cuisine", "beverage", "drink", "beer", "wine", "cheese", "bread", "recipe", "restaurant", "dessert", "confection", "cocktail", "sauce", "soup", "cake", "candy", "chocolate", "tea\\b", "coffee", "brewer", "distiller", "whisk", "snack", "pastry", "noodle", "sausage", "fruit", "vegetable", "chef", "liqueur", "spirit drink", "sandwich", "pie\\b", "biscuit", "cookie")),
    PLANTS("Plants & fungi", "PLANTS", listOf("plant", "flower", "tree\\b", "trees\\b", "fung", "mushroom", "moss\\b", "alga", "orchid", "cultivar", "shrub", "fern\\b", "ferns\\b", "lichen", "botan", "seaweed", "grass", "cactus", "herb\\b", "herbs\\b", "conifer", "palm\\b")),
    ANIMALS("Animals", "ANIMALS", listOf("species", "subspecies", "genus", "taxon", "animal", "bird", "fish", "insect", "beetle", "moth", "butterfl", "spider", "mammal", "reptile", "frog", "snake", "shark", "whale", "dolphin", "snail", "crab", "bat\\b", "bats\\b", "cat\\b", "cats\\b", "dog\\b", "dogs\\b", "horse", "cattle", "breed", "lizard", "turtle", "tortoise", "octopus", "squid", "jellyfish", "worm", "wasp", "bee\\b", "bees\\b", "owl\\b", "parrot", "penguin", "primate", "monkey", "ape\\b", "apes\\b", "rodent", "marsupial", "amphibian", "crustacean", "mollus", "zoo\\b", "gecko", "salamander", "eel\\b", "ant\\b", "ants\\b", "fly\\b", "flies\\b", "mosquito", "elephant", "bear\\b", "wolf", "fox\\b", "deer", "goat", "pig\\b", "sheep", "chicken", "duck", "goose", "pigeon", "crow\\b", "raven", "eagle", "hawk", "falcon", "lobster", "shrimp", "coral", "sponge\\b", "tardigrade")),
    MATHS("Maths & logic", "MATHS", listOf("mathemat", "theorem", "conjecture", "equation", "geometr", "algebra", "paradox", "puzzle", "prime number", "integer", "statistic", "logic", "topolog", "polyhedr", "polygon", "cryptograph", "probabilit", "calculus", "fractal")),
    SCIENCE("Physics & chemistry", "SCIENCE", listOf("physic", "chemi", "chemical element", "compound", "particle", "quantum", "molecul", "isotope", "atom\\b", "atoms\\b", "atomic", "radioact", "laser", "optic", "thermodynam", "magnet", "relativity", "electron", "neutrino", "scientist", "experiment")),
    EARTH("Earth & weather", "EARTH", listOf("volcan", "earthquake", "geolog", "mineral", "glacier", "river", "lake\\b", "lakes\\b", "mountain", "cave\\b", "caves\\b", "weather", "storm", "hurricane", "typhoon", "cyclone", "tornado", "climate", "flood", "tsunami", "rock formation", "waterfall", "desert", "island", "ocean", "sea\\b", "reef", "rainfall", "drought", "geyser", "canyon", "meteorolog", "crystal", "gemstone")),
    TECH("Tech & inventions", "TECH", listOf("software", "computer", "programming", "algorithm", "internet", "website", "robot", "engineer", "invent", "patent", "electronic", "telephone", "radio\\b", "camera", "clock", "machine", "calculator", "nuclear", "steam engine", "technolog", "artificial intelligence", "operating system", "smartphone", "microchip", "encryption")),
    MILITARY("War & military", "MILITARY", listOf("battle", "war\\b", "wars\\b", "wartime", "army", "navy", "naval", "regiment", "military", "soldier", "admiral", "warship", "siege", "weapon", "cannon", "rifle", "tank\\b", "fortress", "air force", "squadron", "battalion", "invasion", "destroyer\\b", "frigate", "battleship", "u-boat", "bomb", "terroris", "insurgen", "guerrilla", "militant", "massacre", "coup\\b")),
    TRANSPORT("Ships, trains & planes", "TRANSPORT", listOf("ship\\b", "ships\\b", "steamship", "locomotive", "railway", "railroad", "train\\b", "trains\\b", "station\\b", "aircraft", "airline", "airport", "aviation", "car\\b", "cars\\b", "automobile", "bridge", "submarine", "boat", "vessel", "tram\\b", "bus\\b", "motorcycle", "road\\b", "highway", "canal", "lighthouse", "tunnel", "airship", "balloon", "bicycle", "metro\\b", "ferry")),
    CRIME("Crime & mysteries", "CRIME", listOf("murder", "crime", "criminal", "heist", "robbery", "thief", "theft", "fraud", "hoax", "prison", "kidnap", "assassinat", "serial killer", "gangster", "pirate", "smuggl", "forger", "unsolved", "mystery", "disappearance", "outlaw", "execution", "con artist", "scam", "espionage", "spy\\b", "spies\\b")),
    MYTH("Myth & the paranormal", "MYTH", listOf("myth", "legend", "folklor", "deity", "god\\b", "gods\\b", "goddess", "ghost", "cryptid", "supernatural", "paranormal", "monster", "witch", "dragon", "fairy", "fairies", "occult", "ufo", "conspiracy", "curse", "haunted", "superstition", "vampire", "werewolf", "mermaid", "alchem")),
    RELIGION("Religion", "RELIGION", listOf("religio", "saint", "bishop", "monaster", "monk", "nun\\b", "nuns\\b", "church", "cathedral", "temple", "mosque", "synagogue", "theolog", "pope", "priest", "christian", "islam", "buddhis", "hindu", "jewish", "biblical", "abbey", "relic", "pilgrim")),
    SCREEN("Film & TV", "FILM & TV", listOf("film", "television", "tv series", "tv show", "sitcom", "episode", "actor", "actress", "cinema", "documentary", "anime", "cartoon", "animated", "soap opera", "reality show", "movie", "screenwriter", "broadcaster")),
    MUSIC("Music", "MUSIC", listOf("song", "album", "band\\b", "bands\\b", "singer", "composer", "musician", "opera", "music", "symphony", "concerto", "rapper", "guitar", "piano", "orchestra", "hymn", "choir", "single by", "jazz", "violin", "drummer", "record label", "musical")),
    BOOKS("Books & comics", "BOOKS", listOf("novel", "book", "comic", "manga", "poem", "poet", "author", "writer", "short story", "magazine", "newspaper", "journalist", "publish", "literary", "literature", "playwright", "play by", "fiction", "fanzine", "librar")),
    ART("Art & design", "ART", listOf("painting", "painter", "sculpt", "artist", "artwork", "photograph", "illustrat", "mural", "fresco", "museum", "gallery", "fashion", "designer", "typeface", "font\\b", "architect", "tapestry", "pottery", "ceramic", "stained glass", "cartograph", "map\\b", "maps\\b")),
    SPORT("Sport & games", "SPORT", listOf("footballer", "football", "soccer", "cricket", "baseball", "basketball", "rugby", "tennis", "golf", "hockey", "olympi", "athlet", "sport", "chess", "racing", "racehorse", "boxer", "boxing", "wrestl", "cyclist", "swimmer", "board game", "card game", "tabletop", "jockey", "marathon", "stadium", "mountaineer", "climber", "skater", "skiing", "toy\\b", "toys\\b", "dice", "crossword")),
    WORDS("Words & language", "WORDS", listOf("language", "word\\b", "words\\b", "alphabet", "dialect", "script\\b", "linguist", "idiom", "phrase", "etymolog", "slang", "grammar", "letter\\b", "writing system", "pronunciation", "dictionary", "palindrome", "anagram", "pseudonym", "place name", "toponym")),
    POLITICS("Politics & law", "POLITICS", listOf("politician", "election", "parliament", "law\\b", "laws\\b", "lawyer", "court", "treaty", "political", "president", "prime minister", "senator", "constitution", "legislat", "diplomat", "governor", "mayor", "referendum", "protest", "activist", "judge", "government", "minister\\b", "flag\\b", "anthem", "micronation")),
    MONEY("Money & business", "MONEY", listOf("company", "business", "brand", "bank\\b", "banks\\b", "banking", "currency", "coin\\b", "coins\\b", "banknote", "econom", "stock", "corporation", "retail", "shop\\b", "store\\b", "manufacturer", "trade\\b", "advertis", "entrepreneur", "businessman", "businesswoman", "money", "lottery", "tax\\b", "postage stamp")),
    HISTORY("History & royalty", "HISTORY", listOf("king\\b", "kings\\b", "queen", "emperor", "empress", "dynasty", "ancient", "medieval", "empire", "archaeolog", "pharaoh", "prince", "duke", "duchess", "noble", "monarch", "century", "historian", "roman\\b", "viking", "colonial", "revolution", "aristocra", "earl\\b", "baron", "knight", "castle", "artefact", "artifact", "manuscript", "tomb")),
    PLACES("Places & buildings", "PLACES", listOf("village", "town\\b", "towns\\b", "township", "city", "building", "house\\b", "tower", "street", "neighbourhood", "neighborhood", "municipality", "county", "district", "province", "park\\b", "garden", "hotel", "palace", "monument", "square\\b", "suburb", "settlement", "region", "capital", "country", "skyscraper", "pub\\b", "theatre", "theater", "school", "university", "college", "estate", "manor", "cemetery", "lighthouse", "landmark", "ghost town")),
    PEOPLE("Curious people", "PEOPLE", listOf("explorer", "eccentric", "socialite", "centenarian", "aviator", "philanthropist", "adventurer", "magician", "circus", "stuntman", "stuntwoman", "person\\b", "people\\b", "nobleman", "courtesan", "model\\b", "hermit", "impostor", "showman", "daredevil", "polymath", "inventor", "businessperson", "chef")),
    MISC("Miscellany", "MISCELLANY", emptyList()),
    ;

    internal val pattern: Regex? = keywords.takeIf { it.isNotEmpty() }
        ?.joinToString("|", prefix = "\\b(?:", postfix = ")") { it }
        ?.let { Regex(it, RegexOption.IGNORE_CASE) }
}

object Subjects {
    private val ORDERED = Subject.entries.filter { it != Subject.MISC }

    /**
     * Picks the first matching subject, checking the short [description] before [title] + [hook]
     * (descriptions are curated one-liners, hooks are noisier). Falls back to [Subject.MISC].
     */
    fun classify(description: String?, title: String, hook: String): Subject {
        description?.takeIf { it.isNotBlank() }?.let { d ->
            ORDERED.firstOrNull { it.pattern!!.containsMatchIn(d) }?.let { return it }
        }
        val text = "$title. $hook"
        return ORDERED.firstOrNull { it.pattern!!.containsMatchIn(text) } ?: Subject.MISC
    }
}
