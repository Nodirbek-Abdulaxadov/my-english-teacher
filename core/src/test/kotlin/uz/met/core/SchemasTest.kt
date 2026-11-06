package uz.met.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemasTest {
    @Test
    fun `schema requires every field and forbids extras at every level`() {
        val schema = schemaOf(SessionAnalysis.serializer().descriptor)
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertFalse(schema["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(8, schema["required"]!!.jsonArray.size)
        val errors = schema["properties"]!!.jsonObject["errors"]!!.jsonObject
        assertEquals("array", errors["type"]!!.jsonPrimitive.content)
        val item = errors["items"]!!.jsonObject
        assertEquals(4, item["required"]!!.jsonArray.size)
        assertFalse(item["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("integer", schema["properties"]!!.jsonObject["b2PlusWords"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `every response type can be turned into a schema`() {
        listOf(
            SessionAnalysis.serializer().descriptor,
            GrammarLesson.serializer().descriptor,
            AnswerCheck.serializer().descriptor,
            WordPack.serializer().descriptor,
            BetterVersion.serializer().descriptor,
            ListeningLesson.serializer().descriptor,
            ImportedAudioExercises.serializer().descriptor,
            SpeakingTestResult.serializer().descriptor,
            PrepPack.serializer().descriptor,
            ReplyHints.serializer().descriptor,
        ).forEach { assertTrue(schemaOf(it).containsKey("properties")) }
    }

    @Test
    fun `merging a profile keeps old facts and replaces recurring errors`() {
        val profile = LearnerProfile(name = "Aziz", facts = listOf("works in a bank"), recurringErrors = listOf("articles"))
        val merged = profile.merged(ProfileUpdate(listOf("has a brother", "works in a bank"), listOf("football"), listOf("tenses")), "B1")
        assertEquals(listOf("works in a bank", "has a brother"), merged.facts)
        assertEquals(listOf("tenses"), merged.recurringErrors)
        assertEquals("B1", merged.level)
        assertEquals("Aziz", merged.name)
    }

    @Test
    fun `an empty error assessment keeps the earlier one`() {
        val profile = LearnerProfile(recurringErrors = listOf("articles"))
        assertEquals(listOf("articles"), profile.merged(ProfileUpdate(emptyList(), emptyList(), emptyList())).recurringErrors)
    }

    @Test
    fun `the prompt puts the stable text first and includes the session brief`() {
        val prompt = Prompts.conversationSystem(LearnerProfile(name = "Aziz"), "Role-play: you are a doctor.")
        assertTrue(prompt.startsWith("You are a warm"))
        assertTrue(prompt.indexOf("Name: Aziz") < prompt.indexOf("Role-play: you are a doctor."))
    }

    @Test
    fun `the path has thirteen units of five lessons and walks in order`() {
        assertEquals(13, LearningPath.units.size)
        assertTrue(LearningPath.units.all { it.lessons.size == 5 })
        assertEquals(65, LearningPath.allLessons.map { it.id }.toSet().size)
        assertEquals("u0.words", LearningPath.nextLesson(emptySet())!!.id)
        assertEquals("u0.grammar", LearningPath.nextLesson(setOf("u0.words"))!!.id)
    }

    @Test
    fun `every unit teaches before it asks for a role-play`() {
        LearningPath.units.forEach { unit ->
            val kinds = unit.lessons.map { it.kind }
            assertTrue(kinds.indexOf(LessonKind.WORDS) < kinds.indexOf(LessonKind.SPEAK))
            assertTrue(kinds.indexOf(LessonKind.GRAMMAR) < kinds.indexOf(LessonKind.SPEAK))
            assertTrue(kinds.indexOf(LessonKind.LISTEN) < kinds.indexOf(LessonKind.SPEAK))
        }
    }

    @Test
    fun `the path starts at the unit for the level and earlier units don't block it`() {
        assertEquals(0, LearningPath.startUnitFor("A2"))
        assertEquals(0, LearningPath.startUnitFor("A2/B1"))
        assertEquals(1, LearningPath.startUnitFor("A2+"))
        assertEquals(3, LearningPath.startUnitFor("B1"))
        assertEquals(7, LearningPath.startUnitFor("B1+"))
        assertEquals(10, LearningPath.startUnitFor("B2"))
        assertEquals(10, LearningPath.startUnitFor("C1"))
        assertEquals("u3.words", LearningPath.nextLesson(emptySet(), startUnit = 3)!!.id)
    }

    @Test
    fun `the tutor is told the session level and its guide`() {
        val prompt = Prompts.conversationSystem(LearnerProfile(level = "B2"), null, level = "A2")
        assertTrue(prompt.contains("Level for this session: A2."))
        assertTrue(prompt.contains(Level.guide("A2")))
    }

    @Test
    fun `lower levels get simpler speech, slower pace and shorter rounds`() {
        assertEquals("A2", Level.normalize("A2/B1"))
        assertEquals("A2", Level.normalize("nonsense"))
        assertEquals("B1", Level.higher("A2", "B1"))
        assertTrue(Level.guide("A2") != Level.guide("B1"))
        assertTrue(Level.speechPace("A2") < Level.speechPace("B1"))
        assertEquals(listOf(90, 60, 45), Level.fluencyRounds("A2"))
        assertEquals(listOf(240, 180, 120), Level.fluencyRounds("B2"))
    }

    @Test
    fun `practised phrases reach the role-play brief`() {
        assertEquals("Brief", Prompts.briefWithPhrases("Brief", emptyList()))
        assertTrue(Prompts.briefWithPhrases("Brief", listOf("nice to meet you")).contains("nice to meet you"))
    }

    @Test
    fun `speaking tests rotate through their sets`() {
        assertEquals(SpeakingTests.tasksFor(0), SpeakingTests.tasksFor(3))
        assertFalse(SpeakingTests.tasksFor(0) == SpeakingTests.tasksFor(1))
    }
}
