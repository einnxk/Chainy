[![Build](https://img.shields.io/github/actions/workflow/status/einnxk/Chainy/ci.yml?logo=github)](https://github.com/einnxk/Chainy/actions)
[![GitHub release](https://img.shields.io/github/v/release/einnxk/Chainy?logo=github&color=blue)](https://github.com/einnxk/Chainy/releases)
[![](https://jitpack.io/v/einnxk/Chainy.svg)](https://jitpack.io/#einnxk/Chainy)

# Chainy
Chainy is a small util to help chain, different async requests together and build a final result.


## Quick start
### Artifact & Repository
Start by adding the `jitpack` repository to your project with the artifacts you want to use.
```kts
repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.einnxk:chainy:1.0.0-SNAPSHOT")
}
```
### Building a Chain

```java
package dev.einnik.chainy.example;

import dev.einnik.chainy.Chain;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Example {

    public CompletableFuture<PlayerProfile> loadProfile() {
        public CompletableFuture<PlayerProfile> fetchPlayerProfile(UUID uuid) {
            return Chain.startWith(0, () -> new PlayerDataState(uuid))
                    .then(1, state -> state.withName(loadName(state.uuid())))
                    .then(2, state -> state.withPlayTime(loadPlayTime(state.uuid())))
                    .thenAsynchronously(3, state -> loadFriendsAsync().thenApply(state::withFriends))
                    .then(4, PlayerDataState::buildProfile)
                    .asynchronously();
        }
    }
}
```

## Build
Chainy uses Gradle to handle dependencies & building. <br />

#### Requirements:
* Java 25 JDK or newer
* Git
* Gradlew installed

#### Compiling from source
```sh
git clone https://github.com/einnxk/chainy.git
cd Chainy/
./gradlew clean build
```

## License
Chainy is licensed under the Apache 2 license. Please see the [`LICENSE`](https://github.com/einnxk/parser-lib/blob/master/LICENSE) for more info.