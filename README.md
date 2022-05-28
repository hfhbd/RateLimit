# Module RateLimit

Limit the requests sent to a Ktor server with a timeout.

- [Source code](https://github.com/hfhbd/RateLimit)
- [Docs](https://ratelimit.softwork.app)

## Install

This package is uploaded to MavenCentral and supports JVM and all native targets as well.

````kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("app.softwork:ratelimit:LATEST")
}
````

## Usage

Simple install it in your application module or for a specific route.

```kotlin
val storage: Storage = // Storage implementation, required
install(RateLimit(storage = storage)) {
    limit = 1000
    timeout = 1.hours

    host = { call ->
        call.request.local.remoteHost
    }

    alwaysAllow { host ->
        host.startsWith("foo")
    }

    alwaysBlock { host ->
        host.endsWith("bar")
    }

    skip { call ->
        if (call.request.local.uri == "/login") { // alternative: install at this route only
            RateLimit.SkipResult.ExecuteRateLimit
        } else {
            RateLimit.SkipResult.SkipRateLimit
        }
    }
}
```

## Storage

To persist the rate limiting, you need to implement a `Storage` provider, which use `kotlinx.datetime.Instant`. All
functions are `suspend` to support async `IO` operations.

## License

Apache 2

# Package app.softwork.ratelimit

The package contains the feature `RateLimit` and the `Storage` implementation.
```mermaid
flowchart
    IncomingCall[Incoming Call] --> Skipping{Should skip rate limit check?}
    
    Skipping -->|Yes| Allow[Allow request - Execute next pipeline]
    Skipping -->|No| Host(Retrieve host from call)
    Host --> IsHostAllowed{Is host always allowed?}
    IsHostAllowed -->|No| AlwaysBlock{Should always block host?}
    AlwaysBlock -->|No| Previous[Get previous record for this host from storage]
    Previous --> Exists{Found previous record?}
    Exists -->|Not found| Save[Create new record]
    Save --> Allow
    Exists -->|Found| Reached{Is this trial < limit?}
    Reached -->|Below limit| Update[Increase trial]
    Update --> Allow
    Reached -->|Reached ratelimit| IsTimeoutOver{Request after last timeout?}
    IsTimeoutOver -->|Yes| RemoveFromStorage[Remove last record from storage]
    RemoveFromStorage --> Allow
    
    IsTimeoutOver --->|No| Block
    
    IsHostAllowed -->|Yes| Allow
    AlwaysBlock -->|Yes| Block[Block call]
   
    
    Block --> ShouldSendRetryHeader{Should Send Retry Header?}
    ShouldSendRetryHeader -->|Yes| AddHeader[Add Header: RetryAfter]
    ShouldSendRetryHeader -->|No| Respond
    AddHeader --> Respond[Respond with HttpCode 429: Too Many Requests]
```
