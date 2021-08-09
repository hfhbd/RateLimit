import java.util.*

plugins {
    kotlin("jvm") version "1.5.21"
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
}

dependencies {
    // Apache 2, https://github.com/ktorio/ktor/releases/latest
    val ktorVersion = "1.6.2"

    api("io.ktor:ktor-server-core:$ktorVersion")

    testImplementation(kotlin("test"))
    // Apache 2, https://github.com/JetBrains/Exposed/releases/latest
    val exposedVersion = "0.33.1"
    testImplementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    testImplementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    testImplementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")

    // EPL 1.0, https://github.com/h2database/h2database/releases/latest
    testRuntimeOnly("com.h2database:h2:1.4.200")
}

tasks.dokkaHtml {
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

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])
    }
    publications.all {
        if (this is MavenPublication) {
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
