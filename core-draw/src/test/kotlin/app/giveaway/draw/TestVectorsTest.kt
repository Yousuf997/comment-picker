package app.giveaway.draw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Published test vectors (docs/test-vectors-v1.json). Every implementation of draw v1 — this one, the Python
 * reference in tools/reference-impl and the open web verifier — must produce exactly these results.
 * Regenerate after an intended change with `./gradlew :core-draw:test -PregenerateVectors=true` (then review).
 */
class TestVectorsTest {

    private val file = File("../docs/test-vectors-v1.json")

    private class Case(val name: String, val entries: Entries, val winners: Int, val alternates: Int) {
        /** Seeds are derived from the case name so the vectors are reproducible. */
        val seed: ByteArray = sha256("draw-v1-vector:$name".toByteArray())
    }

    private sealed interface Entries {
        val usernames: List<String>

        data class Listed(override val usernames: List<String>) : Entries

        /** Large lists are described by a rule instead of being spelled out: prefix + zero-padded 1..count. */
        data class Generated(val prefix: String, val count: Int) : Entries {
            override val usernames = (1..count).map { "$prefix${it.toString().padStart(count.toString().length, '0')}" }
        }
    }

    private val cases = listOf(
        Case("basic", Entries.Listed("ana bilal chen dana eve farah gus hana ivan jo".split(" ")), 3, 2),
        Case("single-entry", Entries.Listed(listOf("solo")), 1, 0),
        Case("single-entry-with-alternates", Entries.Listed(listOf("solo")), 3, 2),
        Case("empty", Entries.Listed(emptyList()), 1, 1),
        Case("more-winners-than-entries", Entries.Listed(listOf("ali", "bea", "cyd")), 5, 0),
        Case("zero-alternates", Entries.Generated("fan", 20), 5, 0),
        Case("alternates-only", Entries.Listed(listOf("kim", "lee", "max", "nia")), 0, 2),
        Case("mixed-case", Entries.Listed(listOf("Zoe", "adam", "BOB", "Carla_99", "dan.k")), 2, 1),
        Case("unicode-usernames", Entries.Listed(listOf("josé", "jöhn", "محمد", "李雷", "ñandú", "zoë", "Émile")), 3, 2),
        Case("code-point-order", Entries.Listed(listOf("ｚeta", "𝒜lpha", "zed", "Ａlpha")), 2, 2),
        Case("duplicates-allowed", Entries.Listed(listOf("ana", "ana", "ana", "bob", "cid")), 2, 1),
        Case("all-same-person", Entries.Listed(listOf("ana", "ANA", "Ana", "ana", "ana")), 2, 2),
        Case("large-1000", Entries.Generated("user", 1000), 10, 5),
    )

    @Test
    fun implementationMatchesThePublishedVectors() {
        val generated = generate()
        if (System.getProperty("regenerateVectors") == "true") {
            file.writeText(prettyJson.encodeToString(JsonObject.serializer(), generated) + "\n")
        }
        assertTrue("Missing ${file.canonicalPath}; run with -PregenerateVectors=true", file.exists())
        val published = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(generated, published)
    }

    @Test
    fun publishedVectorsCoverTheRequiredCases() {
        val names = Json.parseToJsonElement(file.readText()).jsonObject["vectors"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue(names.size >= 12)
        // Spec: one-entry and zero-entry lists, more winners than entries, Unicode usernames.
        assertTrue(names.containsAll(listOf("single-entry", "empty", "more-winners-than-entries", "unicode-usernames")))
    }

    @Test
    fun everyVectorVerifies() {
        Json.parseToJsonElement(file.readText()).jsonObject["vectors"]!!.jsonArray.map { it.jsonObject }.forEach { v ->
            val case = cases.single { it.name == v["name"]!!.jsonPrimitive.content }
            val picks = v["picks"]!!.jsonArray.map {
                val p = it.jsonObject
                Pick(
                    position = p["position"]!!.jsonPrimitive.int,
                    username = p["username"]!!.jsonPrimitive.content,
                    role = Role.valueOf(p["role"]!!.jsonPrimitive.content),
                )
            }
            val result = Verifier.verify(
                v["commitHash"]!!.jsonPrimitive.content,
                v["seed"]!!.jsonPrimitive.content.hexToBytes(),
                CanonicalEntryList.of(case.entries.usernames),
                case.winners,
                case.alternates,
                picks,
            )
            assertEquals(case.name, VerificationResult.Ok, result)
        }
    }

    private fun generate(): JsonObject = buildJsonObject {
        put("algorithm", ALGORITHM_V1)
        put("specification", "docs/draw-spec-v1.md")
        put("vectors", buildJsonArray { cases.forEach { add(vector(it)) } })
    }

    private fun vector(case: Case): JsonObject {
        val list = CanonicalEntryList.of(case.entries.usernames)
        val outcome = DrawV1.select(case.seed, list, case.winners, case.alternates)
        return buildJsonObject {
            put("name", case.name)
            put("seed", case.seed.toHex())
            put("commitHash", Commit.commitHash(case.seed))
            when (val entries = case.entries) {
                is Entries.Listed -> putJsonArray("entries") { entries.usernames.forEach { add(JsonPrimitive(it)) } }
                is Entries.Generated -> put(
                    "generatedEntries",
                    buildJsonObject {
                        put("prefix", entries.prefix)
                        put("count", entries.count)
                    },
                )
            }
            put("winners", case.winners)
            put("alternates", case.alternates)
            put("entryListHash", list.hashHex)
            put(
                "picks",
                JsonArray(
                    outcome.picks.map {
                        buildJsonObject {
                            put("position", it.position)
                            put("username", it.username)
                            put("role", it.role.name)
                        }
                    },
                ),
            )
        }
    }

    private companion object {
        val prettyJson = Json { prettyPrint = true }
    }
}
