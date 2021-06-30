plugins {
    kotlin("multiplatform") version "1.5.20"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.6.0"
    id("org.jetbrains.dokka") version "1.4.32"
    `maven-publish`
}

group = "app.softwork"

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()

    jvm()

    sourceSets {
        // Apache 2, https://github.com/ktorio/ktor/releases/latest
        val ktorVersion = "1.6.1"

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                api("io.ktor:ktor-server-core:$ktorVersion")
            }
        }
        val jvmTest by getting {
            dependencies {
                // Apache 2, https://github.com/JetBrains/Exposed/releases/latest
                val exposedVersion = "0.32.1"
                implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

                implementation("io.ktor:ktor-server-test-host:$ktorVersion")

                // EPL 1.0, https://github.com/h2database/h2database/releases/latest
                runtimeOnly("com.h2database:h2:1.4.200")
            }
        }
    }
}

tasks.named<org.jetbrains.dokka.gradle.DokkaTask>("dokkaHtml") {
    dokkaSourceSets {
        configureEach {
            moduleName by "RateLimit"
            includes.from("README.md")
            reportUndocumented by true
            val sourceSetName = name
            sourceLink {
                localDirectory by file("src/$sourceSetName/kotlin")
                remoteUrl by uri("https://github.com/hfhbd/RateLimit/tree/main/src/$sourceSetName/kotlin").toURL()
                remoteLineSuffix by "#L"
            }
            externalDocumentationLink {
                url by uri("https://ratelimit.softwork.app/").toURL()
            }
        }
    }
}

infix fun<T> Property<T>.by(value: T) {
    set(value)
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/hfhbd/ratelimit")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
