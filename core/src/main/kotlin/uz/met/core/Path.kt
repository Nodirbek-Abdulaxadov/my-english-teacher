package uz.met.core

enum class LessonKind(val labelUz: String) {
    SPEAK("Suhbat"),
    GRAMMAR("Grammatika"),
    WORDS("So'zlar"),
    LISTEN("Tinglash"),
    FLUENCY("Ravonlik"),
}

/** [brief] is written for Claude, or for the learner where the kind shows it to them. */
data class PathLesson(
    val id: String,
    val kind: LessonKind,
    val titleUz: String,
    val brief: String,
)

data class PathUnit(val id: Int, val titleUz: String, val level: String, val lessons: List<PathLesson>)

/** The learning path, walked in order. Generated content is cached per lesson. */
object LearningPath {
    private fun unit(
        id: Int,
        title: String,
        level: String,
        speak: Pair<String, String>,
        grammar: Pair<String, String>,
        words: Pair<String, String>,
        listen: Pair<String, String>,
        fluency: Pair<String, String>,
    ) = PathUnit(
        id, title, level,
        listOf(
            // From taking language in to producing it: the role-play comes once there is something to use.
            PathLesson("u$id.words", LessonKind.WORDS, words.first, words.second),
            PathLesson("u$id.grammar", LessonKind.GRAMMAR, grammar.first, grammar.second),
            PathLesson("u$id.listen", LessonKind.LISTEN, listen.first, listen.second),
            PathLesson("u$id.speak", LessonKind.SPEAK, speak.first, speak.second),
            PathLesson("u$id.fluency", LessonKind.FLUENCY, fluency.first, fluency.second),
        ),
    )

    val units: List<PathUnit> = listOf(
        unit(
            0, "Birinchi qadamlar", "A2",
            "Salomlashish va tanishuv" to "Role-play: you meet the learner for the first time at a friend's house. Say hello, ask their name and where they are from, then about their family and what they do every day. Keep it very simple and friendly.",
            "Am, is, are va a, an" to "The verb to be (am, is, are) and the articles a and an, for talking about yourself, your family and your job",
            "Oila va kundalik ishlar" to "Greetings, family members, and everyday routines such as wake up, go to work and have lunch",
            "Yangi qo'shnilar" to "Two new neighbours say hello and talk about their families, in very simple English",
            "Mening kunim" to "Say who you are, talk about your family, and describe what you do on a normal day.",
        ),
        unit(
            1, "Tanishuv va small talk", "A2+",
            "Yangi hamkasb bilan tanishuv" to "Role-play: you are a friendly new colleague meeting the learner at the office coffee machine. Make small talk about work, home and free time.",
            "Hozirgi zamonlar" to "Present simple versus present continuous, for talking about habits and what is happening now",
            "Tanishuv iboralari" to "Meeting people, small talk and talking about yourself",
            "Birinchi uchrashuv" to "Two people meet at a friend's party and find out what they have in common",
            "O'zingiz haqingizda" to "Talk about yourself: where you live, what you do and what you enjoy.",
        ),
        unit(
            2, "Bo'lib o'tgan voqealar", "A2+",
            "Dam olish kunlari haqida" to "Ask the learner what they did last weekend, then dig into the details so they keep telling the story in the past.",
            "O'tgan zamon" to "Past simple versus past continuous, for telling stories",
            "Voqea aytib berish" to "Telling stories: sequencing words and common past-tense verbs",
            "Kechikkan avtobus" to "A friend tells another about a chaotic morning trip to work",
            "Esda qolgan kun" to "Tell the story of a day you remember well: what happened, and how you felt.",
        ),
        unit(
            3, "Ish va kasb", "B1",
            "Ishingiz haqida" to "You are a curious acquaintance. Ask the learner about their job, what they have done in it and how long they have worked there.",
            "Present Perfect" to "Present perfect versus past simple, including for and since",
            "Ish lug'ati" to "Work, colleagues, meetings and responsibilities",
            "Yangi loyiha" to "Two colleagues talk over how a new project is going",
            "Mening ishim" to "Explain what you do at work or study, and what you like and dislike about it.",
        ),
        unit(
            4, "Sayohat", "B1",
            "Aeroportda" to "Role-play: you are an airline check-in agent, and there is a small problem with the learner's booking that they have to sort out with you.",
            "Kelasi zamon shakllari" to "Future forms: will, going to and present continuous for plans; prepositions of time and place",
            "Sayohat iboralari" to "Airports, hotels, directions and travel problems",
            "Yo'qolgan chamadon" to "A traveller explains to an airline employee that their suitcase has not arrived",
            "Orzuimdagi sayohat" to "Describe a trip you would like to take: where, why, and what you would do there.",
        ),
        unit(
            5, "Restoran va xizmatlar", "B1",
            "Restoranda buyurtma" to "Role-play: you are a waiter. Take the learner's order, and let there be a small mistake with the food that they have to raise politely.",
            "Muloyim so'rovlar" to "Polite requests with could and would; countable and uncountable nouns with some, any, much and many",
            "Xizmat iboralari" to "Restaurants, shops, paying and asking for things politely",
            "Telefonda buyurtma" to "Someone phones a restaurant to book a table and has a special request",
            "Sevimli taomim" to "Describe a dish you love and explain how it is made, as if to a foreign friend.",
        ),
        unit(
            6, "Sog'liq", "B1",
            "Shifokor qabulida" to "Role-play: you are a doctor. Ask the learner about their symptoms, then give simple advice and answer their questions.",
            "Maslahat va majburiyat" to "Should, have to, must and don't have to, for advice and obligation",
            "Sog'liq lug'ati" to "Symptoms, doctors, medicine and healthy habits",
            "Dorixonada" to "A customer asks a pharmacist for advice on a bad cold",
            "Sog'lom turmush" to "Explain what you do to stay healthy, and what you would like to change.",
        ),
        unit(
            7, "Fikr bildirish", "B1+",
            "Shahar yoki qishloq?" to "Discuss whether life is better in a big city or a small town. Disagree politely now and then, so the learner has to defend their opinion.",
            "Solishtirish va bog'lovchilar" to "Comparatives and superlatives, and linking ideas with although, however and because of",
            "Fikr iboralari" to "Giving opinions, agreeing and disagreeing politely",
            "Telefonlar haqida bahs" to "Two friends disagree about whether children should have smartphones",
            "Sizning fikringiz" to "Do people use social media too much? Give your opinion with reasons and an example.",
        ),
        unit(
            8, "Rejalar va orzular", "B1+",
            "Kelajak rejalari" to "Ask the learner about their plans and dreams for the next five years, and ask what they would do in different imagined situations.",
            "Shart gaplar" to "First and second conditionals",
            "Rejalar lug'ati" to "Goals, plans, ambitions and making decisions",
            "Chet elga ko'chish" to "Two friends talk through the pros and cons of moving abroad for work",
            "Agar imkonim bo'lsa" to "If you had one free year and enough money, what would you do, and why?",
        ),
        unit(
            9, "Muammolarni hal qilish", "B1+",
            "Shikoyat qilish" to "Role-play: you are a customer service agent. The learner bought something that broke after a week and wants it sorted out. Don't give in immediately.",
            "Majhul nisbat" to "The passive voice in everyday situations: it was broken, it has been sent",
            "Muammo iboralari" to "Complaints, problems, solutions and being firm but polite",
            "Internet ishlamayapti" to "A customer calls their internet provider about a repeated problem",
            "Hal qilingan muammo" to "Describe a problem you solved: what went wrong, and what you did about it.",
        ),
        unit(
            10, "Ish intervyusi", "B2",
            "Intervyuga tayyorlanish" to "Role-play: you are a professional hiring manager. Interview the learner for a job they choose, and ask real follow-up questions.",
            "Davomli Perfect va nisbiy gaplar" to "Present perfect continuous, and relative clauses with who, which and where",
            "Intervyu iboralari" to "Job interviews, skills, experience and achievements",
            "Intervyudan keyin" to "A candidate tells a friend how a difficult job interview went",
            "Kuchli tomonlarim" to "Answer the classic question: tell me about yourself, and why should we hire you?",
        ),
        unit(
            11, "Yangiliklar va hikoyalar", "B2",
            "Eshitgan yangiligingiz" to "Ask the learner about something interesting they read or heard recently, and have them report what was said and what happened before that.",
            "Ko'chirma gap" to "Reported speech, and the past perfect for what happened earlier",
            "Yangilik lug'ati" to "News, events, reporting what people said, and reacting to stories",
            "Mahalliy yangilik" to "Two neighbours discuss a surprising event in their area",
            "Ta'sir qilgan voqea" to "Tell a story from the news or your life that affected you, and say what people said about it.",
        ),
        unit(
            12, "Munozara va ishontirish", "B2",
            "Ishontirib ko'ring" to "Role-play: you are the learner's manager, and sceptical. They have to persuade you to let them work from home two days a week.",
            "Murakkab shartlar va taxmin" to "Third conditional, wish, and modals of deduction: must have, can't have, might have",
            "Munozara iboralari" to "Persuading, conceding a point, hedging and discourse markers such as on the other hand and to be fair",
            "Ishdan bo'shash qarori" to "Two friends discuss a big decision one of them regrets",
            "Agar boshqacha bo'lganda" to "Describe a decision you would make differently now. What would have happened, and what do you wish you had done?",
        ),
    )

    val allLessons: List<PathLesson> = units.flatMap { it.lessons }

    fun lesson(id: String): PathLesson? = allLessons.firstOrNull { it.id == id }

    fun unitOf(lessonId: String): PathUnit? = units.firstOrNull { unit -> unit.lessons.any { it.id == lessonId } }

    /**
     * The first lesson not done yet, from unit [startUnit] on. Lessons before it stay open for
     * repeating, as do the units before the start, and later lessons stay locked.
     */
    fun nextLesson(done: Set<String>, startUnit: Int = 0): PathLesson? =
        units.filter { it.id >= startUnit }.flatMap { it.lessons }.firstOrNull { it.id !in done }

    /** The first unit pitched at the learner's level, so nobody starts above what they can do. */
    fun startUnitFor(level: String): Int =
        (units.firstOrNull { Level.rank(it.level) >= Level.rank(level) } ?: units.first { it.level == units.last().level }).id
}

data class Scenario(val id: String, val titleUz: String, val descriptionUz: String, val brief: String)

object Scenarios {
    val all = listOf(
        Scenario("free", "Erkin suhbat", "Istalgan mavzuda gaplashing", "Free conversation. Follow the learner's lead, and if they have nothing in mind, start with something from their life."),
        Scenario("day", "Kuningiz qanday o'tdi?", "Bugun nima bo'lganini aytib bering", "Ask the learner how their day went, and keep them telling you about it with real follow-up questions."),
        Scenario("interview", "Ish intervyusi", "Haqiqiy intervyu mashqi", "Role-play: you are a professional hiring manager interviewing the learner for a job they choose. Stay in the role."),
        Scenario("smalltalk", "Small talk", "Notanish odam bilan yengil suhbat", "Role-play: you and the learner are strangers waiting at a conference coffee table. Keep it light and natural."),
        Scenario("doctor", "Shifokor qabulida", "Belgilarni tushuntirish", "Role-play: you are a doctor and the learner is your patient. Ask about symptoms and give simple advice."),
        Scenario("airport", "Aeroportda", "Ro'yxatdan o'tish va muammolar", "Role-play: you are an airline agent. There is a small problem with the learner's flight for them to sort out."),
        Scenario("hotel", "Mehmonxonada", "Xona olish va shikoyat", "Role-play: you are a hotel receptionist. The learner is checking in, and their room isn't what they booked."),
        Scenario("restaurant", "Restoranda", "Buyurtma berish", "Role-play: you are a waiter taking the learner's order. Offer recommendations and ask questions."),
        Scenario("shop", "Do'konda", "Kiyim almashtirish yoki qaytarish", "Role-play: you are a shop assistant. The learner wants to return something without a receipt."),
        Scenario("meeting", "Ish yig'ilishi", "G'oyani tushuntirish", "Role-play: you are a colleague in a short meeting. Ask the learner to explain an idea of theirs and question it politely."),
        Scenario("debate", "Bahs", "Fikringizni himoya qiling", "Pick an everyday topic the learner has an opinion on, take the opposite side, and disagree politely so they have to defend their view."),
    )

    fun byId(id: String): Scenario = all.firstOrNull { it.id == id } ?: all.first()
}

data class TestTask(val promptUz: String, val promptEn: String)

/** The speaking tests rotate, so the monthly test isn't the same questions every time. */
object SpeakingTests {
    private val sets = listOf(
        listOf(
            TestTask("O'zingiz, ishingiz yoki o'qishingiz haqida gapirib bering.", "Tell me about yourself and your work or studies."),
            TestTask("O'tgan dam olish kuningizni aytib bering.", "Tell me what you did last weekend."),
            TestTask("Sizningcha, ingliz tilini o'rganishning eng yaxshi yo'li qaysi? Nima uchun?", "What do you think is the best way to learn English, and why?"),
        ),
        listOf(
            TestTask("Eng yaqin do'stingizni tasvirlab bering.", "Describe your closest friend."),
            TestTask("Siz uchun qiyin bo'lgan bir kunni aytib bering.", "Tell me about a day that was difficult for you."),
            TestTask("Texnologiya hayotimizni yaxshilaydimi yoki yomonlashtiradimi?", "Does technology make our lives better or worse?"),
        ),
        listOf(
            TestTask("Yashayotgan shahringizni chet ellik mehmonga tasvirlab bering.", "Describe your city to a foreign visitor."),
            TestTask("Hayotingizdagi muhim qarorni aytib bering.", "Tell me about an important decision in your life."),
            TestTask("Yoshlar katta shaharga ko'chishi kerakmi? Fikringizni asoslang.", "Should young people move to big cities? Explain your opinion."),
        ),
    )

    fun tasksFor(testNumber: Int): List<TestTask> = sets[testNumber.mod(sets.size)]
}
