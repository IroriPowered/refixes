# Scripts

## Porting worksheet

When porting to a new Hytale version, `worksheet` lists every Mixin whose Hytale targets changed between two server JARs. For each Mixin it shows the switch, the injectors with their method selectors and `@At` targets, and a unified diff of each changed target member. For missing members it shows the old member and the closest members in the new class.

```sh
python3 scripts/verify.py worksheet --java-home "$JAVA_HOME" \
  --hyinit-jar Hyinit.jar --refixes-jar build/bundle/refixes-X.X.X.jar \
  --baseline-server-jar Server-old.jar --server-jar Server-new.jar \
  --source-old hss-old --source-new hss-new --output-dir verify
```

The Refixes JAR must be built for the old version, since its Mixin set is the one checked. `--source-old` and `--source-new` are optional checkouts of the Hytale shared source at the commits of the two servers, for example `git -C hytale-shared-source worktree add --detach ../hss-old <commit>`. With them the diffs show Java source, without them normalized bytecode. The result is `verify/worksheet/worksheet.md`, with the raw data in `worksheet.json`. `all` does not run it.

## Live checks

`live` boots one server with assets and checks two fixes that need live stores. It adds a chunk loader, runs `/chunk unload` on its column and expects the command to succeed and the column to be loaded again. It also starts with a shared instance world whose removal Hytale has already begun and expects Refixes to leave that world to Hytale.

```sh
python3 scripts/verify.py live --java-home "$JAVA_HOME" \
  --hyinit-jar Hyinit.jar --refixes-jar build/bundle/refixes-X.X.X.jar \
  --server-jar Server.jar --assets Assets.zip --output-dir verify
```

The server log is `verify/live/logs/live.log`. `all` does not run it, and only one Hytale server can run on a host at a time.
