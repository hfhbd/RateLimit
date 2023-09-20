import io.gitlab.arturbosch.detekt.*
import java.util.*

plugins {
    kotlin("multiplatform") version "1.9.10"
    id("org.jetbrains.dokka") version "1.9.0"
    id("maven-publish")
    id("signing")
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2"
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    id("app.cash.licensee") version "1.7.0"
}

group = "app.softwork"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
    
    jvm()

    iosX64()
    iosArm64()
    iosArm32()
    iosSimulatorArm64()

    watchosX86()
    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    macosX64()
    macosArm64()
    linuxX64()

    explicitApi()

    sourceSets {
        // Apache 2, https://github.com/ktorio/ktor/releases/latest
        val ktorVersion = "2.3.4"

        commonMain {
            dependencies {
                api("io.ktor:ktor-server-core:$ktorVersion")
                api("io.ktor:ktor-server-cors:$ktorVersion")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))

                implementation("io.ktor:ktor-server-test-host:$ktorVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }

        val jvmTest by getting {
            dependencies {
                // Apache 2, https://github.com/JetBrains/Exposed/releases/latest
                val exposedVersion = "0.43.0"
                implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

                implementation("io.ktor:ktor-server-test-host:$ktorVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-auth:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

                // EPL 1.0, https://github.com/h2database/h2database/releases/latest
                runtimeOnly("com.h2database:h2:2.2.224")
            }
        }
    }
}

tasks.dokkaHtml {
    dokkaSourceSets {
        configureEach {
            moduleName by "RateLimit"
            includes.from("README.md")
            reportUndocumented by true
            val sourceSetName = name
            File("src/$sourceSetName/kotlin").takeIf { it.exists() }?.let {
                sourceLink {
                    localDirectory by it
                    remoteUrl by uri("https://github.com/hfhbd/RateLimit/tree/main/src/$sourceSetName/kotlin").toURL()
                    remoteLineSuffix by "#L"
                }
            }
        }
    }
}

infix fun <T> Property<T>.by(value: T) {
    set(value)
}

nexusPublishing {
    repositories {
        sonatype {
            username.set(System.getProperty("sonartype.apiKey") ?: System.getenv("SONARTYPE_APIKEY"))
            password.set(System.getProperty("sonartype.apiToken") ?: System.getenv("SONARTYPE_APITOKEN"))
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

val emptyJar by tasks.registering(Jar::class) { }

publishing {
    publications.configureEach {
        this as MavenPublication
        artifact(emptyJar) {
            classifier = "javadoc"
        }
        pom {
            name by "app.softwork RateLimit Library"
            description by "A ratelimit plugin for Ktor"
            url by "https://github.com/hfhbd/RateLimit"
            licenses {
                license {
                    name by "The Apache License, Version 2.0"
                    url by "https://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            developers {
                developer {
                    id by "hfhbd"
                    name by "Philip Wedemann"
                    email by "mybztg+mavencentral@icloud.com"
                }
            }
            scm {
                connection by "scm:git://github.com/hfhbd/RateLimit.git"
                developerConnection by "scm:git://github.com/hfhbd/RateLimit.git"
                url by "https://github.com/hfhbd/RateLimit"
            }
        }
    }
}

(System.getProperty("signing.privateKey") ?: System.getenv("SIGNING_PRIVATE_KEY"))?.let {
    String(Base64.getDecoder().decode(it)).trim()
}?.let { key ->
    println("found key, config signing")
    signing {
        val signingPassword = System.getProperty("signing.password") ?: System.getenv("SIGNING_PASSWORD")
        useInMemoryPgpKeys(key, signingPassword)
        sign(publishing.publications)
    }
}

detekt {
    source.from(files(rootProject.rootDir))
    autoCorrect = true
    parallel = true
    buildUponDefaultConfig = true
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${detekt.toolVersion}")
    dokkaPlugin("com.glureau:html-mermaid-dokka-plugin:0.4.6")
}

tasks {
    fun SourceTask.config() {
        include("**/*.kt")
        exclude("**/*.kts")
        exclude("**/resources/**")
        exclude("**/generated/**")
        exclude("**/build/**")
    }
    withType<DetektCreateBaselineTask>().configureEach {
        config()
    }
    withType<Detekt>().configureEach {
        config()

        reports {
            sarif.required.set(true)
        }
    }
}

kover {
    verify {
        onCheck.set(true)
        rule {
            bound {
                minValue = 100
            }
        }
    }
    filters {
        classes {
            excludes += listOf("org.h2.*")
        }
    }
}

licensee {
    allow("Apache-2.0")
    allow("MIT")
}
