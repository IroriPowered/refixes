import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import sys
import time
import zipfile


FIXTURES = Path(__file__).resolve().parent / "local-verification"
REPO = Path(__file__).resolve().parents[1]
PROBE_SOURCES = (
    "CompatibilityProbe", "MixinCompatibilityMain", "MixinCompatibilityServer", "MixinCompatibilitySetup",
    "SectionBackoffRegression", "SharedInstanceStorageRegression", "NativePatchRegression",
    "CommandBufferRemovalRegression", "EntityGuardRegression", "NettySetupPacingRegression", "RegressionRunner",
)
SERVICE_TESTS = ("ChunkLoaderRegression", "CoalescingPersistenceRegression")
SERVICE_SOURCES = ("main/java/cc/irori/refixes/service/ChunkLoaderService", "v07/java/cc/irori/refixes/service/ChunkLoaderServiceV07",
                   "v08/java/cc/irori/refixes/service/ChunkLoaderServiceV08", "main/java/cc/irori/refixes/service/CoalescingPersistence",
                   "main/java/cc/irori/refixes/service/OwnedKeepLoaded")
SHARED_POLICY_TESTS = (
    ("WrapperRegression", "wrappers"), ("WrapperRegression", "capability"),
    ("PolicyMixinConfigRegression",), ("VersionTableRegression",),
)
POLICY_TESTS = (
    ("StoreShutdownRegression",), ("ConfigUpgradeRegression",), ("WatchdogPolicyRegression",), ("WorldPolicyRegression",),
    ("WorldPolicyTransferRegression",), ("RecoveryPolicyRegression",), ("RepulsionPolicyRegression",),
    ("TargetFingerprintRegression",),
)
FINGERPRINTS = "META-INF/hyinit/fingerprints.json"
SERVER_MAIN = "com/hypixel/hytale/Main.class"
QUEUE_BASE = {"OutgoingQueueMaxBytes": 8192, "OutgoingQueueMaxPackets": 0}
QUEUE_RUNS = (
    ("broadcast-wait", {}, {"commonParallelism": 1}),
    ("broadcast-no-wait", {"OutgoingQueueBroadcastWaitMs": 0}, {"broadcastWaitMs": 0}),
    ("broadcast-large-queue", {"OutgoingQueueMaxBytes": 1048576, "OutgoingQueueBroadcastStallMs": 1000},
     {"maxBytes": 1048576, "broadcastStallMs": 1000, "mode": "large", "commonParallelism": 1}),
)
LIVE_WORLD = "instance-shared-live"
LIVE_INSTANCE = {"RemovalConditions": [{"Type": "Timeout", "TimeoutSeconds": 0}],
                 "EventTitleOnRemoval": {"PrimaryTitle": {"RawText": "Closing"}, "DurationS": 6.0,
                                         "FadeInDurationS": 0.0, "FadeOutDurationS": 0.0}}
LIVE_FORBIDDEN = ("Exception while running that command", r"Unloaded \d+ shared instance world",
                  "Failed to unload shared instance world")
LIVE_STEP_SECONDS = 60
LIVE = []


class Failure(RuntimeError):
    pass


def heap(value):
    if not re.fullmatch(r"[1-9][0-9]*[mMgG]", value):
        raise argparse.ArgumentTypeError("use a bounded heap such as 768m or 4g")
    return value


def positive(value):
    if int(value) <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return int(value)


def arguments():
    parser = argparse.ArgumentParser(description=(
        "Verify built Hyinit and Refixes JARs against a Hytale server JAR. "
        "apply: every active mixin merges in default and all-switches mode, with probe regressions. "
        "regressions: plain JVM tests. queue-limits: outgoing queue limits in Hyinit JVMs. "
        "all: every one of them. "
        "worksheet: the Hytale changes behind each changed mixin, not part of all. "
        "live: one asset boot that runs /chunk unload on a chunk loader column and leaves a shared instance world "
        "that Hytale already removes to Hytale, not part of all."))
    parser.add_argument("command", choices=("apply", "regressions", "queue-limits", "worksheet", "live", "all"))
    for name in ("java-home", "hyinit-jar", "refixes-jar", "server-jar", "output-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--baseline-server-jar", type=Path, help="pinned server JAR, required by worksheet")
    parser.add_argument("--source-old", type=Path, help="worksheet: shared source checkout matching the baseline server")
    parser.add_argument("--source-new", type=Path, help="worksheet: shared source checkout matching the current server")
    parser.add_argument("--assets", type=Path, help="matching asset directory or ZIP, adds the apply setup phase, required by live")
    parser.add_argument("--full-boot", action="store_true", help="apply also boots each mode and stops it cleanly")
    parser.add_argument("--server-heap", type=heap, default="4g")
    parser.add_argument("--direct-memory", type=heap, default="1g")
    parser.add_argument("--bind", default="127.0.0.1:0", help="full-boot bind address")
    parser.add_argument("--live-seconds", type=positive, default=3, help="full-boot run time before stop")
    parser.add_argument("--stop-timeout", type=positive, default=60, help="seconds for a clean stop")
    parser.add_argument("--timeout", type=positive, default=360, help="seconds per process (default: 360)")
    parser.add_argument("--keep-runtime", action="store_true", help="keep world and prefab cache files of apply")
    args = parser.parse_args()
    if args.command == "worksheet" and args.baseline_server_jar is None:
        parser.error(f"{args.command} needs --baseline-server-jar")
    if args.full_boot and args.assets is None:
        parser.error("--full-boot needs --assets")
    if args.command == "live" and args.assets is None:
        parser.error("live needs --assets")
    if (args.source_old is None) != (args.source_new is None):
        parser.error("--source-old and --source-new go together")
    return args


def stop(process):
    for action in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(process.pid, action)
        except ProcessLookupError:
            break
        try:
            process.wait(timeout=10)
            break
        except subprocess.TimeoutExpired:
            pass


def run(ctx, name, command, cwd, boot=False):
    command = list(map(str, command))
    log_path = ctx.out / "logs" / (name + ".log")
    print(f"[{name}] {shlex.join(command)}", flush=True)
    started = time.monotonic()
    ready = stopping = None
    with log_path.open("wb") as log:
        process = subprocess.Popen(command, cwd=cwd, env=ctx.env, stdin=subprocess.PIPE if boot else subprocess.DEVNULL,
                                   stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        LIVE.append(process)
        try:
            while process.poll() is None:
                now = time.monotonic()
                if boot and ready is None and (cwd / "boot-result.json").is_file():
                    ready = now
                if ready is not None and stopping is None and now - ready >= ctx.args.live_seconds:
                    process.stdin.write(b"stop\n")
                    process.stdin.flush()
                    stopping = now
                if stopping is not None and now - stopping > ctx.args.stop_timeout:
                    raise Failure(f"{name}: no clean stop within {ctx.args.stop_timeout}s")
                if stopping is None and now - started > ctx.args.timeout:
                    raise Failure(f"{name}: timed out after {ctx.args.timeout}s")
                time.sleep(0.2)
        finally:
            LIVE.remove(process)
            stop(process)
    seconds = round(time.monotonic() - started, 2)
    ctx.phases[name] = {"seconds": seconds, "exitCode": process.returncode, "log": str(log_path)}
    ctx.save()
    if process.returncode != 0:
        print(log_path.read_text(errors="replace")[-12000:], file=sys.stderr, flush=True)
        raise Failure(f"{name} exited {process.returncode}, inspect {log_path}")
    if boot:
        text = log_path.read_text(errors="replace")
        if stopping is None or "Shutdown completed!" not in text or any(marker in text for marker in (
                "Exception while shutting down:", "Forcing shutdown!", "Failed to shutdown ", "Failed to run task!")):
            print(text[-12000:], file=sys.stderr, flush=True)
            raise Failure(f"{name}: no clean boot and stop, inspect {log_path}")


def javac(ctx, name, classpath, destination, sources):
    destination.mkdir(parents=True)
    run(ctx, name, [ctx.java_home / "bin/javac", "-J-Xmx256m", "--release", "25", "-proc:none",
                    "-cp", os.pathsep.join(map(str, classpath)), "-d", destination, *sources], ctx.out)


def main_class(source):
    package = re.search(r"^package ([\w.]+);", source.read_text(), re.M).group(1)
    return package + "." + source.stem


def fixtures(names):
    return [FIXTURES / (name + ".java") for name in names]


def probe_jar(classes, destination, name, mixins):
    config = name + ".mixins.json"
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob("*.class")):
            jar.write(path, path.relative_to(classes).as_posix())
        jar.writestr("manifest.json", json.dumps({
            "Group": "LocalVerification", "Name": name, "Version": "1.0.0", "Mixins": [config]}))
        jar.writestr(config, json.dumps({
            "required": True, "minVersion": "0.8", "package": "local.hyinit.verify.mixin",
            "mixins": mixins, "injectors": {"defaultRequire": 1}}))


def server_dir(ctx, path, *plugins):
    (path / "earlyplugins").mkdir(parents=True)
    (path / "HytaleServer.jar").symlink_to(ctx.server)
    for plugin in (ctx.refixes, *plugins):
        shutil.copy2(plugin, path / "earlyplugins" / plugin.name)
    return path


def read_json(path):
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError):
        return {}


def asset_fingerprint(assets):
    files = [assets] if assets.is_file() else sorted(path for path in assets.rglob("*") if path.is_file())
    return [(str(path), path.stat().st_size, path.stat().st_mtime_ns) for path in files]


def server_as_built(ctx):
    running = server_version(ctx.server)
    with zipfile.ZipFile(ctx.refixes) as jar:
        if FINGERPRINTS not in jar.namelist():
            raise Failure(f"{FINGERPRINTS} is missing from {ctx.refixes}")
        targets = sorted(json.loads(jar.read(FINGERPRINTS)).get("servers", {}))
    if not targets:
        raise Failure(f"{FINGERPRINTS} in {ctx.refixes} names no Hytale version")
    minor = ".".join(running.split(".")[:2]) + "."
    version = next((target for target in targets if target.startswith(minor)), targets[-1])
    path = ctx.out / "server-as-built.jar"
    with zipfile.ZipFile(ctx.server) as server, zipfile.ZipFile(path, "w") as jar:
        jar.writestr("META-INF/MANIFEST.MF", f"Manifest-Version: 1.0\nImplementation-Version: {version}\n\n")
        jar.writestr(SERVER_MAIN, server.read(SERVER_MAIN))
    return path


def apply(ctx):
    assets = ctx.args.assets.expanduser().resolve() if ctx.args.assets else None
    if assets is not None and not (assets.is_dir() or zipfile.is_zipfile(assets)):
        raise Failure(f"assets must be a directory or ZIP: {assets}")
    classes = ctx.out / "probe-classes"
    javac(ctx, "compile-probe", ctx.jars, classes,
          fixtures((*PROBE_SOURCES, "SharedInstanceStorageRegression" + hytale_line(ctx.server))))
    helper = ctx.out / "local-verification-only.jar"
    probe_jar(classes, helper, "CompatibilityProbe",
              ["MixinCompatibilityMain", "MixinCompatibilitySetup", "MixinCompatibilityServer"])
    java = [ctx.java_home / "bin/java", f"-Xmx{ctx.args.server_heap}", "-Xms128m", "-Xverify:all",
            "-XX:ActiveProcessorCount=2", f"-XX:MaxDirectMemorySize={ctx.args.direct_memory}",
            "-Djava.awt.headless=true", "-Djoml.nounsafe=true", "--add-opens", "java.base/sun.misc=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "-Dmixin.debug.export=true", "-Dmixin.debug.countInjections=true"]
    launch = ["-jar", ctx.hyinit, "--server-jar", ctx.server, "--accept-early-plugins", "--disable-sentry"]
    before = asset_fingerprint(assets) if assets else None
    as_built = server_as_built(ctx)
    for mode in ("default", "all"):
        runtime = server_dir(ctx, ctx.out / "runtime" / mode, helper)
        run(ctx, mode + "-prepare", [ctx.java_home / "bin/java", "-Xmx256m", "-cp",
                                     os.pathsep.join(map(str, (as_built, helper, *ctx.jars))),
                                     "local.hyinit.verify.CompatibilityProbe", ctx.hyinit, ctx.refixes, mode], runtime)
        run(ctx, mode, [*java, *launch], runtime)
        if read_json(runtime / "verification-result.json").get("status") != "passed":
            raise Failure(f"{mode}: probe did not pass")
        if assets is None:
            continue
        asset_args = [f"--assets={assets}", "--auth-mode=offline", f"--bind={ctx.args.bind}",
                      f"--prefab-cache={runtime / 'prefab-cache'}", "--disable-cpb-build", "--disable-file-watcher"]
        run(ctx, mode + "-setup", [*java, "-Dlocal.verify.setupRegression=true", *launch, *asset_args], runtime)
        if read_json(runtime / "setup-result.json").get("status") != "setup-verified":
            raise Failure(f"{mode}: setup regressions did not pass")
        if ctx.args.full_boot:
            run(ctx, mode + "-boot", [*java, "-Dlocal.verify.fullBoot=true", *launch, *asset_args], runtime, boot=True)
            boot = read_json(runtime / "boot-result.json")
            if boot.get("status") != "booted" or boot.get("pluginState") != "ENABLED":
                raise Failure(f"{mode}: full boot did not enable Refixes")
        if not ctx.args.keep_runtime:
            for name in ("universe", "prefab-cache"):
                shutil.rmtree(runtime / name, ignore_errors=True)
    if assets is not None and asset_fingerprint(assets) != before:
        raise Failure("a run changed the shared assets")


def regressions(ctx):
    java = [ctx.java_home / "bin/java", "-Xverify:all", "-Xmx256m", "-XX:ActiveProcessorCount=2",
            "-Djava.util.concurrent.ForkJoinPool.common.parallelism=2", f"-Drefixes.fixtures={FIXTURES / 'configs'}", f"-Drefixes.docs={REPO / 'docs'}"]
    service = ctx.out / "service-classes"
    plugin = REPO / "plugin/src"
    javac(ctx, "compile-service", [service, *ctx.jars], service,
          [*sorted((FIXTURES / "sections-stubs").rglob("*.java")),
           *(plugin / (name + ".java") for name in SERVICE_SOURCES), *fixtures((*SERVICE_TESTS, "RegressionRunner"))])
    policy = ctx.out / "policy-classes"
    javac(ctx, "compile-policy", ctx.jars, policy,
          fixtures(sorted({test[0] for test in (*POLICY_TESTS, *SHARED_POLICY_TESTS)} | {"RegressionRunner"})))
    shared = [":".join((main_class(FIXTURES / (name + ".java")), *test_args)) for name, *test_args in SHARED_POLICY_TESTS]
    tests = [(service, name) for name in SERVICE_TESTS] + [(policy, *test) for test in POLICY_TESTS] + [
        (policy, "RegressionRunner", *shared)]
    as_built = server_as_built(ctx)
    for classes, name, *test_args in tests:
        label = "SharedPolicyRegressions" if name == "RegressionRunner" else "-".join((name, *test_args))
        cwd = ctx.out / "runtime" / label
        cwd.mkdir(parents=True)
        temporary = ctx.out / "tmp" / label
        temporary.mkdir(parents=True)
        run(ctx, label, [*java, f"-Djava.io.tmpdir={temporary}", "-cp", os.pathsep.join(map(str, (as_built, classes, *ctx.jars))),
                         main_class(FIXTURES / (name + ".java")), *test_args], cwd)
        shutil.rmtree(temporary)


def queue_limits(ctx):
    classes = ctx.out / "queue-classes"
    javac(ctx, "compile-queue", ctx.jars, classes, fixtures(("QueueLimitsRegression", "MixinQueueLimitsMain", "RegressionRunner")))
    helper = ctx.out / "queue-limits-verification.jar"
    probe_jar(classes, helper, "QueueLimitsRegression", ["MixinQueueLimitsMain"])
    for name, early, expected in QUEUE_RUNS:
        runtime = server_dir(ctx, ctx.out / "runtime" / name, helper)
        config = runtime / "mods" / "IroriPowered_Refixes"
        config.mkdir(parents=True)
        (config / "Refixes.json").write_text(json.dumps({"OtherServerVersions": "AllPatches"}) + "\n")
        (config / "config.json").write_text(json.dumps({"Early": {**QUEUE_BASE, **early}}) + "\n")
        run(ctx, name, [ctx.java_home / "bin/java", "-Xmx1g", "-XX:MaxDirectMemorySize=256m",
                        "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                        "-Djava.awt.headless=true", "-Djoml.nounsafe=true",
                        *([f"-Djava.util.concurrent.ForkJoinPool.common.parallelism={expected['commonParallelism']}"]
                          if "commonParallelism" in expected else []),
                        *(f"-Dqueue-limits.{key}={value}" for key, value in expected.items()),
                        "-jar", ctx.hyinit, "--server-jar", ctx.server, "--accept-early-plugins", "--disable-sentry"],
            runtime)
        if read_json(runtime / "queue-limits-result.json").get("status") != "passed":
            raise Failure(f"{name}: queue limit regression did not pass")


def server_version(path):
    with zipfile.ZipFile(path) as jar:
        for line in jar.read("META-INF/MANIFEST.MF").decode().splitlines():
            if line.startswith("Implementation-Version:"):
                return line.split(":", 1)[1].strip()
    return path.name


def hytale_line(server):
    major, minor = map(int, server_version(server).split("-")[0].split(".")[:2])
    return "V08" if (major, minor) >= (0, 8) else "V07"


def listed_mixins(ctx):
    with zipfile.ZipFile(ctx.refixes) as jar:
        mixins = [config["package"] + "." + name
                  for config in (json.loads(jar.read(entry)) for entry in json.loads(jar.read("manifest.json"))["Mixins"])
                  for name in config.get("mixins", [])]
    if not mixins:
        raise Failure(f"no mixins listed in {ctx.refixes}")
    return mixins


def source_index(root):
    if root is None:
        return None
    root = root.expanduser().resolve()
    index = {}
    for java_root in sorted(root.glob("HytaleServer/**/src/main/java")):
        for path in java_root.rglob("*.java"):
            index.setdefault(path.relative_to(java_root).as_posix(), path)
    if not index:
        raise Failure(f"no HytaleServer sources under {root}")
    return index


def method_source(index, code):
    path = index.get(code.get("source") or "")
    if path is None or code["first"] < 0:
        return None
    lines = path.read_text(errors="replace").splitlines()
    first, last = code["first"] - 1, min(code["last"], len(lines))
    name = code["source"].rsplit("/", 1)[1][:-5].split("$")[0] if code["name"] == "<init>" else code["name"]
    if name.startswith(("lambda$", "<")):
        return lines[first:last]
    declaration = re.compile(r"\b" + re.escape(name) + r"\s*\(")
    start = next((n for n in range(first, max(first - 40, -1), -1) if declaration.search(lines[n])), None)
    if start is None:
        return lines[first:last]
    while start > 0 and lines[start - 1].strip().startswith("@"):
        start -= 1
    depth, opened = 0, False
    for end in range(start, len(lines)):
        text = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//.*', "", lines[end])
        depth += text.count("{") - text.count("}")
        opened = opened or "{" in text
        if opened and depth <= 0 and end >= last - 1:
            return lines[start:end + 1]
    return lines[start:last]


def member_text(code, index):
    text = method_source(index, code) if index is not None else None
    if text is not None:
        return [line + "\n" for line in text], "java"
    return [line + "\n" for line in code["bytecode"].splitlines()], "bytecode"


def lambda_key(code):
    return re.sub(r"(lambda\$\w+\$)\d+", r"\1N", code["name"]) + code["desc"]


def member_diff(member, old_index, new_index):
    pairs = {}
    for side in ("old", "new"):
        for code in member[side]:
            pairs.setdefault(lambda_key(code), {})[side] = code
    diffs = []
    for key, sides in pairs.items():
        before = member_text(sides["old"], old_index) if "old" in sides else ([], None)
        after = member_text(sides["new"], new_index) if "new" in sides else ([], None)
        if None not in (before[1], after[1]) and before[1] != after[1]:
            before, after = member_text(sides["old"], None), member_text(sides["new"], None)
        diff = "".join(difflib.unified_diff(before[0], after[0], "old/" + key, "new/" + key))
        if not diff and "java" in (before[1], after[1]):
            before = member_text(sides["old"], None) if "old" in sides else ([], None)
            after = member_text(sides["new"], None) if "new" in sides else ([], None)
            diff = "".join(difflib.unified_diff(before[0], after[0], "old/" + key, "new/" + key))
        if diff:
            diffs.append(diff)
    return diffs


def nearest(member):
    name = re.split(r"[(:]", member["member"].split("->")[0].strip())[0].rsplit(";", 1)[-1].rstrip("*+")
    names = sorted({re.split(r"[(:]", candidate)[0] for candidate in member.get("candidates", [])})
    close = difflib.get_close_matches(name, names, n=5, cutoff=0.5)
    return [candidate for wanted in close for candidate in member["candidates"] if re.split(r"[(:]", candidate)[0] == wanted]


def worksheet(ctx):
    baseline = ctx.args.baseline_server_jar.expanduser().resolve()
    mixins = listed_mixins(ctx)
    old_index, new_index = source_index(ctx.args.source_old), source_index(ctx.args.source_new)
    classes = ctx.out / "worksheet-classes"
    javac(ctx, "compile-worksheet", [ctx.hyinit, ctx.refixes], classes, fixtures(("TargetDrift", "PortingWorksheet")))
    data_path = ctx.out / "worksheet.json"
    classpath = os.pathsep.join(map(str, (classes, ctx.hyinit, ctx.refixes, baseline)))
    run(ctx, "worksheet", [ctx.java_home / "bin/java", "-Xmx1g", "-cp", classpath, "local.refixes.drift.PortingWorksheet",
                           ctx.refixes, baseline, ctx.server, data_path, *mixins], ctx.out)
    data = json.loads(data_path.read_text())
    entries = sorted(data["mixins"], key=lambda entry: (entry["switch"] or "~", entry["mixin"]))
    count = sum(len(entry["members"]) for entry in entries)
    source = "Java source" if old_index else "normalized bytecode"
    lines = [f"# Porting worksheet, Hytale {data['baseline']} to {data['current']}", "",
             f"{len(entries)} of {data['checked']} mixins of Hytale {data['baseline']} have {count} changed or missing "
             f"target members. Diffs show {source}.", ""]
    current_switch = object()
    for entry in entries:
        if entry["switch"] != current_switch:
            current_switch = entry["switch"]
            lines += [f"## {current_switch or 'No switch'}", ""]
        lines += [f"### {entry['mixin']}", "", "Targets: " + ", ".join(f"`{name}`" for name in entry["targets"]), ""]
        for injector in entry["injectors"]:
            at = "".join(f", at `{point}`" for point in injector["at"])
            methods = ", ".join(f"`{method}`" for method in injector["methods"])
            lines.append(f"- @{injector['annotation']} `{injector['handler']}` on {methods}{at}")
        lines.append("")
        for member in entry["members"]:
            moved = f", moved to `{member['movedTo']}`" if member.get("movedTo") else ""
            lines += [f"#### {member['kind']} `{member['member']}` {member['status']}{moved}", "",
                      f"In `{member['target']}`.", ""]
            if member["kind"] == "class":
                lines += ["Classes with the same name: " + (", ".join(f"`{name}`" for name in member["classCandidates"])
                                                          or "none"), ""]
                continue
            if member["status"] == "changed":
                for diff in member_diff(member, old_index, new_index):
                    lines += ["```diff", diff.rstrip("\n"), "```", ""]
                continue
            for code in member["old"]:
                text, kind = member_text(code, old_index)
                lines += [f"Old {code['name']}{code.get('desc', '')}:", "", "```" + ("java" if kind == "java" else ""),
                          "".join(text).rstrip("\n"), "```", ""]
            for code in member.get("sameName", []):
                text, kind = member_text(code, new_index)
                lines += [f"New overload {code['name']}{code['desc']}:", "", "```" + ("java" if kind == "java" else ""),
                          "".join(text).rstrip("\n"), "```", ""]
            candidates = nearest(member)
            lines += ["Nearest in the new class: " + (", ".join(f"`{name}`" for name in candidates) or "none"), ""]
    sheet = ctx.out / "worksheet.md"
    sheet.write_text("\n".join(lines))
    ctx.result.update(mixins=len(entries), members=count)
    print(f"{len(entries)} mixins with {count} changed or missing members, worksheet {sheet}")


def live(ctx):
    assets = ctx.args.assets.expanduser().resolve()
    if not (assets.is_dir() or zipfile.is_zipfile(assets)):
        raise Failure(f"assets must be a directory or ZIP: {assets}")
    runtime = server_dir(ctx, ctx.out / "runtime")
    for world, plugin in (("default", {}), (LIVE_WORLD, {"Instance": LIVE_INSTANCE})):
        config = runtime / "universe" / "worlds" / world / "config.json"
        config.parent.mkdir(parents=True)
        config.write_text(json.dumps({"WorldGen": {"Type": "Flat"}, "Plugin": plugin}, indent=2) + "\n")
    loaded = r"Chunk 5, 7:\n(?:\t.*\n)*\t.*Section 0:"
    unloaded = [r'Unloaded chunk at 5, 7 in "default"!']
    if hytale_line(ctx.server) == "V08":
        unloaded.append(r"Unloaded \d+ sections of chunk 5, 7 in default held by chunk loader 'live'")
    steps = (
        ("boot", None, False, ["Hytale Server Booted!"]),
        ("shared instance world left to Hytale", None, False,
         [f"Hytale is already removing shared instance world '{LIVE_WORLD}', leaving it to Hytale",
          "No shared instance worlds to unload"]),
        ("Hytale removes the shared instance world", None, False, [f"Removing individual world: {LIVE_WORLD}"]),
        ("chunk loader added", "chunkloader add --world=default --x=5 --z=7 --label=live", False,
         [r"Added chunk loader at 5, 7 \(live\)"]),
        ("chunk loader column loaded", "chunk info 5 7 --world=default", True, [loaded]),
        ("chunk unload of the chunk loader column", "chunk unload 5 7 --world=default", False, unloaded),
        ("chunk loader column loaded again", "chunk info 5 7 --world=default", True, [loaded]),
    )
    command = [ctx.java_home / "bin/java", f"-Xmx{ctx.args.server_heap}", "-Xms128m",
               f"-XX:MaxDirectMemorySize={ctx.args.direct_memory}", "-Djava.awt.headless=true",
               "-jar", ctx.hyinit, "--server-jar", ctx.server, "--accept-early-plugins", "--disable-sentry",
               f"--assets={assets}", "--auth-mode=offline", f"--bind={ctx.args.bind}"]
    command = list(map(str, command))
    log_path = ctx.out / "logs" / "live.log"
    print(f"[live] {shlex.join(command)}", flush=True)
    started = time.monotonic()
    failures = []
    with log_path.open("wb") as log:
        process = subprocess.Popen(command, cwd=runtime, env=ctx.env, stdin=subprocess.PIPE, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        LIVE.append(process)
        try:
            for label, send, repeat, patterns in steps:
                if failures:
                    break
                offset = log_path.stat().st_size if send else 0
                deadline = time.monotonic() + (ctx.args.timeout if label == "boot" else LIVE_STEP_SECONDS)
                sent = None
                while True:
                    text = log_path.read_bytes()[offset:].decode(errors="replace")
                    missing = [pattern for pattern in patterns if not re.search(pattern, text)]
                    if not missing:
                        print(f"[live] PASS {label}", flush=True)
                        break
                    if process.poll() is not None or time.monotonic() > deadline:
                        failures.append(f"{label}: the log has no {missing[0]!r}")
                        break
                    if send and (sent is None or repeat and time.monotonic() - sent >= 3):
                        process.stdin.write((send + "\n").encode())
                        process.stdin.flush()
                        sent = time.monotonic()
                    time.sleep(0.5)
            if process.poll() is None:
                process.stdin.write(b"stop\n")
                process.stdin.flush()
                try:
                    process.wait(timeout=ctx.args.stop_timeout)
                except subprocess.TimeoutExpired:
                    failures.append(f"no clean stop within {ctx.args.stop_timeout}s")
        finally:
            LIVE.remove(process)
            stop(process)
    ctx.phases["live"] = {"seconds": round(time.monotonic() - started, 2), "exitCode": process.returncode,
                          "log": str(log_path)}
    text = log_path.read_text(errors="replace")
    failures += [f"the log has {marker!r}" for marker in LIVE_FORBIDDEN if re.search(marker, text)]
    removals = text.count(f"Removing individual world: {LIVE_WORLD}")
    if removals > 1:
        failures.append(f"{LIVE_WORLD} was removed {removals} times")
    if process.returncode != 0 or "Shutdown completed!" not in text:
        failures.append(f"no clean boot and stop, exit code {process.returncode}")
    if failures:
        raise Failure(". ".join(failures) + f". Inspect {log_path}")


COMMANDS = {"apply": apply, "regressions": regressions, "queue-limits": queue_limits, "worksheet": worksheet,
            "live": live}


class Context:
    def __init__(self, args, out, java_home, jars, env):
        self.args, self.out, self.java_home, self.jars, self.env = args, out, java_home, jars, env
        self.hyinit, self.refixes, self.server = jars
        self.phases = {}
        self.result = {"status": "running", "phases": self.phases}
        (out / "logs").mkdir(parents=True)

    def save(self):
        (self.out / "result.json").write_text(json.dumps(self.result, indent=2) + "\n")


def main():
    args = arguments()
    java_home = args.java_home.expanduser().resolve()
    jars = tuple(path.expanduser().resolve() for path in (args.hyinit_jar, args.refixes_jar, args.server_jar))
    extra = [args.baseline_server_jar.expanduser().resolve()] if args.baseline_server_jar else []
    for path in (java_home / "bin/java", java_home / "bin/javac", *jars, *extra):
        if not path.is_file():
            raise Failure(f"not a file: {path}")
    version = subprocess.run([java_home / "bin/java", "-version"], capture_output=True, text=True).stderr
    if not re.search(r'version "(2[5-9]|[3-9][0-9])[."+-]', version):
        raise Failure(f"JDK 25 or newer is required: {version.strip()}")
    env = dict(os.environ, JAVA_HOME=str(java_home), PATH=str(java_home / "bin") + os.pathsep + os.environ.get("PATH", ""))
    for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "GRADLE_OPTS"):
        env.pop(key, None)
    output = args.output_dir.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for path in (*jars, *extra):
        with path.open("rb") as stream:
            hashes[str(path)] = hashlib.file_digest(stream, "sha256").hexdigest()
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
    failed = []
    for command in [name for name in COMMANDS if name not in ("worksheet", "live")] if args.command == "all" else (args.command,):
        out = output / command
        try:
            out.mkdir()
        except FileExistsError:
            raise Failure(f"output already exists: {out}")
        ctx = Context(args, out, java_home, jars, env)
        ctx.result["jars"] = hashes
        started = time.monotonic()
        try:
            COMMANDS[command](ctx)
            ctx.result["status"] = "passed"
            print(f"PASS {command}: {out}", flush=True)
        except Exception as error:
            ctx.result.update(status="failed", error=str(error))
            failed.append(command)
            print(f"FAIL {command}: {error}", file=sys.stderr, flush=True)
        finally:
            ctx.result["seconds"] = round(time.monotonic() - started, 2)
            ctx.save()
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Failure as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
    finally:
        for process in list(LIVE):
            stop(process)
