import java.net.URI
import java.security.MessageDigest

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val umpireSpecVersion: String by project
val umpireSpecSha256: String by project

// ---------------------------------------------------------------------------
// Conformance fixture download + extraction
// ---------------------------------------------------------------------------
// Pulls umpire-spec from GitHub releases, verifies SHA-256, and extracts
// the conformance/ directory into build/conformance/.
//
// Paths in index.json are relative to index.json itself (i.e. build/conformance/).
// Tests read the system property "umpire.conformance.dir" to locate index.json.
//
// Version is baked into the cached tarball filename so Gradle's UP-TO-DATE check
// automatically re-downloads when umpireSpecVersion changes in gradle.properties.

val conformanceTgz: Provider<RegularFile> =
    layout.buildDirectory.file("conformance-cache/umpire-spec-$umpireSpecVersion.tar.gz")

val conformanceOut: Provider<Directory> =
    layout.buildDirectory.dir("conformance")

val downloadConformanceTarball by tasks.registering {
    group = "verification"
    description = "Downloads umpire-spec $umpireSpecVersion conformance fixtures from GitHub"
    outputs.file(conformanceTgz)
    doLast {
        val dest = conformanceTgz.get().asFile
        dest.parentFile.mkdirs()
        val url = "https://github.com/umpire-tools/umpire-spec/archive/refs/tags/$umpireSpecVersion.tar.gz"
        logger.lifecycle("Downloading $url")
        URI(url).toURL().openStream().use { src -> dest.outputStream().use { src.copyTo(it) } }

        // Verify SHA-256
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(dest.inputStream().use { it.readBytes() })
            .joinToString("") { byte -> String.format("%02x", byte) }
        if (actual != umpireSpecSha256) {
            throw RuntimeException(
                "SHA-256 mismatch for $umpireSpecVersion: expected $umpireSpecSha256, got $actual"
            )
        }
    }
}

val extractConformanceFixtures by tasks.registering(Copy::class) {
    group = "verification"
    description = "Extracts conformance/ from the umpire-spec tarball into build/conformance/"
    dependsOn(downloadConformanceTarball)
    from(tarTree(resources.gzip(conformanceTgz.get().asFile))) {
        include("umpire-spec-${umpireSpecVersion.drop(1)}/conformance/**")
        // Strip "umpire-spec-X.Y.Z/conformance/" — output: index.json, fixtures/, failures/
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(2).toTypedArray())
        }
    }
    into(conformanceOut)
    includeEmptyDirs = false
}

// ---------------------------------------------------------------------------
// Kotlin Multiplatform
// ---------------------------------------------------------------------------

kotlin {
    jvm()

    jvmToolchain(17)

    // Future targets — add as needed:
    // ios()
    // js { browser() }
    // wasmJs()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:5.9.1")
                implementation("io.kotest:kotest-assertions-core:5.9.1")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// JVM test wiring
// ---------------------------------------------------------------------------

tasks.withType<Test> {
    dependsOn(extractConformanceFixtures)
    systemProperty("umpire.conformance.dir", conformanceOut.get().asFile.absolutePath)
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------

repositories {
    mavenCentral()
}
