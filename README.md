# Module RateLimit

Limit the requests sent to a Ktor server with a timeout.

- [Source code](https://github.com/hfhbd/RateLimit)
- [Docs](https://ratelimit.softwork.app)

## Install

This package is uploaded
to [GitHub Packages](https://docs.github.com/en/packages/guides/configuring-gradle-for-use-with-github-packages).

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
install(RateLimit) // use the default configuration

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

### InMemory

By default, `RateLimit` uses an in-memory map to store the requests.

```kotlin
install(RateLimit) {
    storage = InMemory()
}
```

### Persistent Storage

To persist the rate limiting, you need to implement a `Storage` provider, which heavily uses the `ExperimentalTime`
classes: `TimeSource` and `TimeStamp`. The `DatabaseStorage` provides a serializable TimeStamp to persist it in a
database.

````kotlin
class DBStorage(private val db: Database) : DatabaseStorage {
    // ... implement the save and fetch methods depending on your database
}

install(RateLimit) {
    storage = DBStorage(db = db)
}
````

You can use
the [DatabaseStorageTest](https://github.com/hfhbd/RateLimit/tree/master/src/jvmTest/kotlin/app/softwork/ratelimit/DatabaseStorageTest.kt)
as a template. All functions are `suspend` to support async `IO` operations.

## License

Apache 2

# Package app.softwork.ratelimit

The package contains the feature `RateLimit`, the `Storage`, an `InMemory` implementation and the `DatabaseStorage`
interface. 
