# R2 marker update regression validation

Related: #1. Base revision: `93b454efb8802dc7406d6873434f2aeec5c636f4`.

## What changes

`CheckWorldTimes` compares border vertices and keeps a deep snapshot after a
successful notification. An equivalent newly allocated polygon does not notify
again. Removing a border notifies once without dereferencing null. Unloaded and
removed worlds lose their snapshots, and a replacement world is tracked separately.

`MarkerImpl.setLocation` skips an identical world and coordinates. A real coordinate
change still updates the client and persistent markers. A world move first removes
the old client marker, then updates the normalized world and notifies the new world;
both worlds' marker files are invalidated. These changes are separate commits.

Storage, rendering intervals, visible marker sets, and platform dependencies are
unchanged. This patch does not suppress spawn, border, or WorldGuard markers.

## Automated checks

Run with the repository's Gradle wrapper and a supported JDK:

```text
./gradlew :DynmapCore:test
./gradlew :spigot:build
```

The normal settings also configure Fabric and Forge. If unrelated platform setup
blocks a focused core check, an untracked settings file can include just
`DynmapCore` and `DynmapCoreAPI`, with their `projectDir` pointing to the original
directories and an unchanged copy of the root `build.gradle` as its root build.
Do not modify module sources or dependencies to make this check pass. This is a
focused check, not proof of all-platform compatibility.

The regression tests execute the actual border polling job and marker notification
path. Cases cover equivalent new polygons, absent borders, add/remove/resize/move,
mutable vertices, unload/reload, removal/re-addition, replacement worlds, unchanged
marker locations, individual coordinate changes, persistence, and old/new world
notifications. They restore the marker API and map manager singletons after use.

Before the fix, five of the initial nine new cases failed. After the fix and two
additional lifecycle/coordinate cases, all 104 core tests passed on Java 21 with
Java 8 source/target compatibility. All-platform builds and live storage behavior
remain separate validation gates.

## Server comparison

1. Record the server/Paper/Java version, source revision, candidate version and
   SHA-256. Compare the candidate's S3 implementation with the currently installed
   artifact so a pre-existing storage customization is not accidentally lost.
2. Start with an isolated test server, fresh disposable world, local-only listeners,
   and separate storage. Do not give a test instance the production R2 prefix.
3. Confirm startup, helper selection, bundled dependencies (including Jetty), tiles,
   spawn/border markers, normal block updates and zoom. Exercise border addition,
   removal, resize and movement, spawn movement and world unload/reload. Test
   ordinary markers and WorldGuard in an environment that includes those plugins.
4. Measure a stable border and then real changes using identical intervals and
   marker visibility before/after. Compare the marker JSON with only its top-level
   `timestamp` removed. A changing timestamp/ETag alone is not a content change.
5. Measure successful `PutObject` counts per marker object at the storage writer or
   provider. Origin `LastModified` advancing while normalized content is constant
   establishes repeated writes, but sampled timestamps do not provide exact counts.
   Cached browser GET responses cannot measure origin writes.
6. Keep dynamic `standalone/dynmap_*.json`, rendered tiles, other markers and reads
   separate. This patch is not expected to eliminate legitimate dynamic updates.

## Production gate and rollback

A passing core test/build or merged PR is not a production rollout. Before a
production canary, retain the original JAR and configuration in a unique backup
directory, record checksums and ownership, and agree on the selected server and
activation window. Preserve the plugin updater's existing policy until a scoped
pin has been reviewed. Never delete existing map tiles or marker persistence.

Stage only the candidate JAR for the agreed restart. Verify the running version,
startup logs, public map, marker sets, actual change propagation and storage writes
after activation. If a check fails, restore the original JAR/configuration and
activate it in the agreed window. Do not infer activation from a copied file or
from a successful build. Keep the issue open until the server comparison is complete.
