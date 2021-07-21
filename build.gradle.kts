plugins {
    kotlin("multiplatform") version "1.5.21"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.6.0"
    id("org.jetbrains.dokka") version "1.5.0"
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
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
    publications.all {
        if (this is MavenPublication) {
            pom {
                name.set("app.softwork RateLimit Library")
                description.set("A ratelimit plugin for Ktor")
                url.set("https://github.com/hfhbd/RateLimit")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("hfhbd")
                        name.set("Philip Wedemann")
                        email.set("mybztg+mavencentral@icloud.com")
                    }
                }
                scm {
                    connection.set("scm:git://github.com/hfhbd/RateLimit.git")
                    developerConnection.set("scm:git://github.com/hfhbd/RateLimit.git")
                    url.set("https://github.com/hfhbd/RateLimit")
                }
            }
        }
    }
}

(System.getProperty("signing.privateKey") ?: System.getenv("SIGNING_PRIVATE_KEY"))?.let {
        String(java.util.Base64.getDecoder().decode(it)).trim()
}?.let { key ->
    println("found key, config signing")
    signing {
        val signingPassword = System.getProperty("signing.password") ?: System.getenv("SIGNING_PASSWORD")
        useInMemoryPgpKeys(key, signingPassword)
        sign(publishing.publications)
    }
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
