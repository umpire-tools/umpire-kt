package dev.umpire.json

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drives the umpire-spec conformance fixtures against the Kotlin implementation.
 *
 * Fixtures are downloaded from GitHub releases (umpire-tools/umpire-spec) during
 * the Gradle build, SHA-256 verified, and extracted to build/conformance/. The
 * directory is passed via the system property "umpire.conformance.dir" — set
 * automatically by the extractConformanceFixtures task.
 *
 * index.json is the discovery manifest. All paths inside it are relative to index.json
 * (i.e. relative to build/conformance/ itself).
 *
 * See spec/conformance/README.md in the umpire-spec repo for the full fixture shape
 * and pseudocode runner spec.
 */
class ConformanceTest : FreeSpec({

    val conformanceDir = File(
        System.getProperty("umpire.conformance.dir")
            ?: error("umpire.conformance.dir not set — run tests via Gradle (./gradlew test)")
    )

    val index = Json.parseToJsonElement(
        conformanceDir.resolve("index.json").readText()
    ).jsonObject

    "fixture cases" - {
        index["fixtures"]!!.jsonArray.forEach { entry ->
            val meta = entry.jsonObject
            val id = meta["id"]!!.jsonPrimitive.content
            val path = meta["path"]!!.jsonPrimitive.content
            val fixture = Json.parseToJsonElement(
                conformanceDir.resolve(path).readText()
            ).jsonObject

            id - {
                // TODO: parse fixture.schema, build Umpire instance, run cases
                // Replace this placeholder with real evaluation once UmpireSchema is implemented.
                fixture["fixtureVersion"]!!.jsonPrimitive.content shouldBe "1"
            }
        }
    }

    "failure fixtures" - {
        index["failures"]!!.jsonArray.forEach { entry ->
            val meta = entry.jsonObject
            val id = meta["id"]!!.jsonPrimitive.content
            val path = meta["path"]!!.jsonPrimitive.content
            val fixture = Json.parseToJsonElement(
                conformanceDir.resolve(path).readText()
            ).jsonObject

            id - {
                // TODO: assert each failure case throws as expected
                fixture["fixtureVersion"]!!.jsonPrimitive.content shouldBe "1"
            }
        }
    }
})
