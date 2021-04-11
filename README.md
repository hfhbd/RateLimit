# RateLimit

Limit the requests sent to a Ktor server with a timeout.

## Install

This package is uploaded to GitHub Packages. See
the [docs](https://docs.github.com/en/packages/guides/configuring-gradle-for-use-with-github-packages) to install this
feature.

````kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/hfhbd/*")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("app.softwork:ratelimit:0.0.2")
}
````

## Usage

Simple install it in your application module.

```kotlin
install(RateLimit) // use default configuration

install(RateLimit) {
    limit = 10
    timeout = 1.hours

    alwaysAllow { host ->
        host.startsWith("foo")
    }
    alwaysBlock { host ->
        host.endsWith("bar")
    }

    host = { call ->
        call.request.local.host
    }
}
```

## Storage

By default, `RateLimit` uses a in-memory map to store the requests data.

```kotlin
install(RateLimit) {
    storage = InMemory()
}
```

To persist the rate limiting, you need to implement a `Storage` provider, which heavily uses the `ExperimentalTime`
classes: `TimeSource` and `TimeStamp`. You can use
the [databaseTest](src/jvmTest/kotlin/app/softwork/ratelimit/DatabasedStorageTest.kt) as a template. All functions
are `suspend` to support async `IO` operations.

## License

Apache 2
