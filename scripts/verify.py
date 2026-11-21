import argparse
import csv
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
    "CommandBufferRemovalRegression", "EntityGuardRegression", "NettySetupPacingRegression",
)
SERVICE_TESTS = ("ChunkLoaderRegression", "CoalescingPersistenceRegression")
SERVICE_SOURCES = ("ChunkLoaderService", "CoalescingPersistence", "OwnedKeepLoaded")
POLICY_TESTS = (
    ("WrapperRegression", "wrappers"), ("WrapperRegression", "capability"),
    ("StoreShutdownRegression",), ("PolicyMixinConfigRegression",), ("ConfigMigrationRegression",),
    ("WatchdogPolicyRegression",), ("WorldPolicyRegression",), ("WorldPolicyTransferRegression",),
    ("RecoveryPolicyRegression",), ("RepulsionPolicyRegression",), ("TargetFingerprintRegression",),
)
FINGERPRINTS = "cc/irori/refixes/early/target-fingerprints.tsv"
SERVER_MAIN = "com/hypixel/hytale/Main.class"
QUEUE_BASE = {"OutgoingQueueMaxBytes": 8192, "OutgoingQueueMaxPackets": 0}
QUEUE_RUNS = (
    ("broadcast-wait", {}, {"commonParallelism": 1}),
    ("broadcast-no-wait", {"OutgoingQueueBroadcastWaitMs": 0}, {"broadcastWaitMs": 0}),
    ("broadcast-large-queue", {"OutgoingQueueMaxBytes": 1048576, "OutgoingQueueBroadcastStallMs": 1000},
     {"maxBytes": 1048576, "broadcastStallMs": 1000, "mode": "large", "commonParallelism": 1}),
)
DRIFT_STATUS = {
    "missing": "missing in the current server",
    "changed": "code changed since the baseline server",
    "added": "only in the current server",
}
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
        "drift: patched methods that changed between a baseline and the current server. all: every one of them."))
    parser.add_argument("command", choices=("apply", "regressions", "queue-limits", "drift", "all"))
    for name in ("java-home", "hyinit-jar", "refixes-jar", "server-jar", "output-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--baseline-server-jar", type=Path, help="pinned server JAR, required by drift and all")
    parser.add_argument("--assets", type=Path, help="matching asset directory or ZIP, adds the apply setup phase")
    parser.add_argument("--full-boot", action="store_true", help="apply also boots each mode and stops it cleanly")
    parser.add_argument("--server-heap", type=heap, default="4g")
    parser.add_argument("--direct-memory", type=heap, default="1g")
    parser.add_argument("--bind", default="127.0.0.1:0", help="full-boot bind address")
    parser.add_argument("--live-seconds", type=positive, default=3, help="full-boot run time before stop")
    parser.add_argument("--stop-timeout", type=positive, default=60, help="seconds for a clean stop")
    parser.add_argument("--timeout", type=positive, default=360, help="seconds per process (default: 360)")
    parser.add_argument("--keep-runtime", action="store_true", help="keep world and prefab cache files of apply")
    args = parser.parse_args()
    if args.command in ("drift", "all") and args.baseline_server_jar is None:
        parser.error(f"{args.command} needs --baseline-server-jar")
    if args.full_boot and args.assets is None:
        parser.error("--full-boot needs --assets")
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
    with zipfile.ZipFile(ctx.refixes) as jar:
        header = jar.read(FINGERPRINTS).decode().partition("\n")[0]
    version = re.fullmatch(r"# Hytale (\S+)", header.strip())
    if version is None:
        raise Failure(f"{FINGERPRINTS} in {ctx.refixes} names no Hytale version")
    path = ctx.out / "server-as-built.jar"
    with zipfile.ZipFile(ctx.server) as server, zipfile.ZipFile(path, "w") as jar:
        jar.writestr("META-INF/MANIFEST.MF", f"Manifest-Version: 1.0\nImplementation-Version: {version[1]}\n\n")
        jar.writestr(SERVER_MAIN, server.read(SERVER_MAIN))
    return path


def apply(ctx):
    assets = ctx.args.assets.expanduser().resolve() if ctx.args.assets else None
    if assets is not None and not (assets.is_dir() or zipfile.is_zipfile(assets)):
        raise Failure(f"assets must be a directory or ZIP: {assets}")
    classes = ctx.out / "probe-classes"
    javac(ctx, "compile-probe", ctx.jars, classes, fixtures(PROBE_SOURCES))
    helper = ctx.out / "local-verification-only.jar"
    probe_jar(classes, helper, "CompatibilityProbe",
              ["MixinCompatibilityMain", "MixinCompatibilitySetup", "MixinCompatibilityServer"])
    java = [ctx.java_home / "bin/java", f"-Xmx{ctx.args.server_heap}", "-Xms128m", "-Xverify:all",
            "-XX:ActiveProcessorCount=2", f"-XX:MaxDirectMemorySize={ctx.args.direct_memory}",
            "-Djava.awt.headless=true", "--add-opens", "java.base/sun.misc=ALL-UNNAMED",
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
            "-Djava.util.concurrent.ForkJoinPool.common.parallelism=2"]
    service = ctx.out / "service-classes"
    plugin = REPO / "plugin/src/main/java/cc/irori/refixes/service"
    javac(ctx, "compile-service", [service, *ctx.jars], service,
          [*sorted((FIXTURES / "sections-stubs").rglob("*.java")),
           *(plugin / (name + ".java") for name in SERVICE_SOURCES), *fixtures(SERVICE_TESTS)])
    policy = ctx.out / "policy-classes"
    javac(ctx, "compile-policy", ctx.jars, policy, fixtures(sorted({test[0] for test in POLICY_TESTS})))
    tests = [(service, name) for name in SERVICE_TESTS] + [(policy, *test) for test in POLICY_TESTS]
    as_built = server_as_built(ctx)
    for classes, name, *test_args in tests:
        label = "-".join((name, *test_args))
        cwd = ctx.out / "runtime" / label
        cwd.mkdir(parents=True)
        stub = () if name == "TargetFingerprintRegression" else (as_built,)
        run(ctx, label, [*java, "-cp", os.pathsep.join(map(str, (*stub, classes, *ctx.jars))),
                         main_class(FIXTURES / (name + ".java")), *test_args], cwd)


def queue_limits(ctx):
    classes = ctx.out / "queue-classes"
    javac(ctx, "compile-queue", ctx.jars, classes, fixtures(("QueueLimitsRegression", "MixinQueueLimitsMain")))
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
                        "-Djava.awt.headless=true",
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


def drift(ctx):
    baseline = ctx.args.baseline_server_jar.expanduser().resolve()
    with zipfile.ZipFile(ctx.refixes) as jar:
        mixins = [config["package"] + "." + name
                  for config in (json.loads(jar.read(entry)) for entry in json.loads(jar.read("manifest.json"))["Mixins"])
                  for name in config.get("mixins", [])]
    if not mixins:
        raise Failure(f"no mixins listed in {ctx.refixes}")
    classes = ctx.out / "drift-classes"
    javac(ctx, "compile-drift", [ctx.hyinit], classes, fixtures(("TargetDrift",)))
    table = ctx.out / "target-drift.tsv"
    run(ctx, "drift", [ctx.java_home / "bin/java", "-Xmx1g", "-cp", os.pathsep.join(map(str, (classes, ctx.hyinit))),
                       "local.refixes.drift.TargetDrift", ctx.refixes, baseline, ctx.server, table, *mixins], ctx.out)
    with table.open(newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    before, after = server_version(baseline), server_version(ctx.server)
    findings = [row for row in rows if row["status"] != "ok"]
    counts = {status: sum(row["status"] == status for row in rows) for status in DRIFT_STATUS}
    targets = len({(row["mixin"], row["target"]) for row in rows})
    for row in findings:
        print(f"{row['mixin']}  {row['kind']}  {row['status']}  {row['target']}  {row['member']}")
    totals = (f"{len(mixins)} mixins, {targets} mixin targets: {counts['missing']} missing, "
              f"{counts['changed']} changed, {counts['added']} added")
    print(f"Hytale {before} (baseline) to {after}: {totals}, table {table}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        lines = [f"### Patched methods, Hytale {before} to {after}", "", totals + ".", ""]
        if findings:
            lines += ["| Mixin | Target | Kind | Member | Status |", "| --- | --- | --- | --- | --- |"]
            lines += [f"| {row['mixin']} | `{row['target']}` | {row['kind']} | `{row['member']}` | "
                      f"{DRIFT_STATUS[row['status']]} |" for row in findings]
        with open(summary, "a") as stream:
            stream.write("\n".join(lines) + "\n\n")
    if counts["missing"] + counts["changed"]:
        raise Failure(f"re-check every mixin listed as missing or changed against Hytale {after}. "
                      f"A changed method still applies, but its patch may no longer be right")


COMMANDS = {"apply": apply, "regressions": regressions, "queue-limits": queue_limits, "drift": drift}


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
    for command in COMMANDS if args.command == "all" else (args.command,):
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
