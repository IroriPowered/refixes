import argparse
import atexit
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
import tempfile
import time
import zipfile


class VerificationError(RuntimeError):
    pass


def positive_seconds(value):
    value = int(value)
    if value <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return value


def heap_size(value):
    if not re.fullmatch(r"[1-9][0-9]*[mMgG]", value):
        raise argparse.ArgumentTypeError("use a bounded JVM heap such as 768m or 4g")
    return value


def arguments():
    repo = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Build and verify Hyinit + Refixes against a local Hytale server JAR.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Examples:
  python3 scripts/verify-local.py --server-jar /path/HytaleServer.jar --java-home /path/jdk-25
  python3 scripts/verify-local.py --server-jar /path/HytaleServer.jar --java-home /path/jdk-25 \\
      --assets /path/Assets.zip --server-heap 4g --timeout 360
  python3 scripts/verify-local.py --server-jar /path/HytaleServer.jar --skip-build \\
      --hyinit-jar build/libs/Hyinit-0.2.2-local.209.jar --refixes-jar ../refixes/build/bundle/Refixes.jar

Default checks: sequential shadowJar/singleJar builds; uninstrumented launcher --help;
real Hyinit launch in default and all-optional modes; VM class linking and strict Mixin
injection counts for every configured target; exported mixin application evidence;
real early-plugin dummy-transformer removal and storage callback regressions.
--assets additionally runs setup-phase regressions; --full-boot boots each configuration,
checks the enabled Refixes runtime plugin,
verifies the section-backoff deadlock fix using the booted World, allows live ticks,
sends the real console stop command, and requires a clean shutdown.
Exit status is nonzero on a failed check or timeout. Logs, release JARs, plans, transformed
bytecode and result.json persist in --output-dir; isolated world files are removed unless
--keep-runtime is supplied. These are compatibility checks, not gameplay correctness tests.
""",
    )
    parser.add_argument("--server-jar", required=True, type=Path, help="local HytaleServer.jar (never modified)")
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"), help="JDK 25+ containing java and javac")
    parser.add_argument("--hyinit-dir", type=Path, default=repo.parent / "hyinit")
    parser.add_argument("--refixes-dir", type=Path, default=repo)
    parser.add_argument("--hyinit-jar", type=Path, help="explicit built launcher JAR (otherwise discovered by manifest)")
    parser.add_argument("--refixes-jar", type=Path, help="explicit built Main+Mixins single JAR")
    parser.add_argument("--skip-build", action="store_true", help="use already built JARs; still compile and run verification helper")
    parser.add_argument("--full-boot", action="store_true",
                        help="also run the memory-heavy full asset boot and clean-stop proof (requires --assets)")
    parser.add_argument("--direct-memory", type=heap_size, default="1g",
                        help="bound on JVM direct memory for server runs (default: 1g)")
    parser.add_argument("--assets", type=Path, help="matching Hytale asset directory or ZIP; enables setup regressions")
    parser.add_argument("--server-heap", type=heap_size, default="4g")
    parser.add_argument("--build-heap", type=heap_size, default="768m")
    parser.add_argument("--timeout", type=positive_seconds, default=360, help="seconds per runtime phase (default: 360)")
    parser.add_argument("--build-timeout", type=positive_seconds, default=900, help="seconds per sequential Gradle build")
    parser.add_argument("--stop-timeout", type=positive_seconds, default=60, help="seconds for clean shutdown after stop")
    parser.add_argument("--live-seconds", type=positive_seconds, default=3, help="full-boot observation before stop")
    parser.add_argument("--bind", default="127.0.0.1:0", help="full-boot server bind address (default: loopback ephemeral port)")
    parser.add_argument("--output-dir", type=Path, help="new durable result directory (must not exist)")
    parser.add_argument("--keep-runtime", action="store_true", help="retain isolated temporary server directories, including worlds")
    return parser.parse_args()


def file_required(path, label):
    path = path.expanduser().resolve()
    if not path.is_file():
        raise VerificationError(f"{label} is not a file: {path}")
    return path


LIVE_PROCESSES = []


def stop_all_processes():
    while LIVE_PROCESSES:
        stop_process(LIVE_PROCESSES.pop())

def stop_process(process):
    if process is None:
        return
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=10)
    except ProcessLookupError:
        try:
            process.wait(timeout=10)
        except Exception:
            pass


def run(command, cwd, env, timeout, log_path, full_boot=False, live_seconds=3, stop_timeout=60):
    print(f"[{log_path.stem}] {shlex.join(map(str, command))}", flush=True)
    started = time.monotonic()
    ready_at = None
    stop_at = None
    clean_boot = False
    with log_path.open("wb") as log:
        log.write(("$ " + shlex.join(map(str, command)) + "\n").encode())
        log.flush()
        process = subprocess.Popen(
            list(map(str, command)), cwd=cwd, env=env, stdin=subprocess.PIPE,
            stdout=log, stderr=subprocess.STDOUT, start_new_session=True,
        )
        LIVE_PROCESSES.append(process)
        try:
            while process.poll() is None:
                now = time.monotonic()
                if full_boot and ready_at is None:
                    try:
                        boot = json.loads((cwd / "boot-result.json").read_text())
                        if boot.get("status") != "booted" or boot.get("pluginState") != "ENABLED":
                            raise VerificationError("Full-boot probe did not confirm enabled Refixes")
                        ready_at = now
                        clean_boot = True
                    except (FileNotFoundError, json.JSONDecodeError):
                        pass
                if ready_at is not None and stop_at is None and now - ready_at >= live_seconds:
                    process.stdin.write(b"stop\n")
                    process.stdin.flush()
                    stop_at = now
                if stop_at is not None:
                    if now - stop_at > stop_timeout:
                        raise VerificationError(f"Clean stop timed out after {stop_timeout}s")
                elif now - started > timeout:
                    raise VerificationError(f"Phase timed out after {timeout}s")
                time.sleep(0.2)
            if process.returncode != 0:
                raise VerificationError(f"Process exited {process.returncode}")
            if full_boot and (not clean_boot or stop_at is None):
                raise VerificationError("Server exited before full boot and console stop")
        finally:
            if process in LIVE_PROCESSES:
                LIVE_PROCESSES.remove(process)
            stop_process(process)
            try:
                process.stdin.close()
            except Exception:
                pass
    text = log_path.read_text(errors="replace")
    if full_boot:
        if ("Shutdown completed!" not in text or "Exception while shutting down:" in text
                or "Forcing shutdown!" in text or "Failed to shutdown " in text):
            raise VerificationError("Server did not complete a clean shutdown; inspect " + str(log_path))
        if "Failed to run task!" in text:
            raise VerificationError("Server task failed during full boot; inspect " + str(log_path))
    return {"seconds": round(time.monotonic() - started, 2), "log": str(log_path), "exitCode": process.returncode}


def jar_matches(path, kind):
    try:
        with zipfile.ZipFile(path) as jar:
            if kind == "hyinit":
                manifest = jar.read("META-INF/MANIFEST.MF").decode()
                return ("Main-Class: cc.irori.hyinit.Main" in manifest
                        and "org/spongepowered/asm/mixin/Mixin.class" in jar.namelist()
                        and "_hyinit.mixins.json" in jar.namelist())
            manifest = json.loads(jar.read("manifest.json"))
            return (manifest.get("Main") == "cc.irori.refixes.Refixes"
                    and "refixes.mixins.json" in manifest.get("Mixins", [])
                    and "cc/irori/refixes/Refixes.class" in jar.namelist())
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return False


def artifact(path, repo, kind):
    if path is not None:
        selected = file_required(path, kind + " JAR")
        if not jar_matches(selected, kind):
            raise VerificationError(f"Not the required {kind} artifact: {selected}")
        return selected
    directories = [repo / "build/libs"] if kind == "hyinit" else [repo / "build/bundle", repo / "build/libs"]
    candidates = [path for directory in directories for path in directory.glob("*.jar") if jar_matches(path, kind)]
    if not candidates:
        raise VerificationError(f"No {kind} release artifact found in {directories}")
    return max(candidates, key=lambda path: path.stat().st_mtime_ns).resolve()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def asset_fingerprint(src):
    if not src.exists():
        raise VerificationError("Assets path does not exist: " + str(src))
    if src.is_file():
        stat = src.stat()
        return {src.name: (stat.st_size, stat.st_mtime_ns)}
    seen = {}
    for path in src.rglob("*"):
        if path.is_file():
            stat = path.stat()
            seen[str(path.relative_to(src))] = (stat.st_size, stat.st_mtime_ns)
    if not seen:
        raise VerificationError("Assets directory is empty: " + str(src))
    return seen


def package_probe(classes, destination):
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob("*.class")):
            jar.write(path, path.relative_to(classes).as_posix())
        jar.writestr("manifest.json", json.dumps({
            "Group": "LocalVerification", "Name": "CompatibilityProbe", "Version": "1.0.0",
            "Mixins": ["local-verification.mixins.json"],
        }))
        jar.writestr("local-verification.mixins.json", json.dumps({
            "required": True, "minVersion": "0.8",
            "package": "local.hyinit.verify.mixin",
            "mixins": ["MixinCompatibilityMain", "MixinCompatibilitySetup", "MixinCompatibilityServer"],
            "injectors": {"defaultRequire": 1},
        }))


def retain_proof(runtime, destination):
    destination.mkdir(parents=True, exist_ok=True)
    names = ["verification-plan.json", "verification-result.json", "verification-failure.txt", "setup-result.json", "boot-result.json", ".mixin.out", "logs"]
    for name in names:
        source = runtime / name
        if source.is_dir():
            shutil.copytree(source, destination / name, dirs_exist_ok=True)
        elif source.is_file():
            shutil.copy2(source, destination / name)
    config = runtime / "mods/IroriPowered_Refixes/Refixes.json"
    if config.is_file():
        shutil.copy2(config, destination / "Refixes.json")
    for source in runtime.glob("hs_err_pid*.log"):
        shutil.copy2(source, destination / source.name)


def main():
    args = arguments()
    server = file_required(args.server_jar, "Server JAR")
    if not zipfile.is_zipfile(server):
        raise VerificationError(f"Server JAR is not a ZIP archive: {server}")
    if args.java_home:
        java_home = Path(args.java_home).expanduser().resolve()
    else:
        found = shutil.which("javac")
        if not found:
            raise VerificationError("Pass --java-home with a JDK 25+ installation")
        java_home = Path(found).resolve().parents[1]
    java = file_required(java_home / "bin/java", "java")
    javac = file_required(java_home / "bin/javac", "javac")
    hyinit_repo = args.hyinit_dir.expanduser().resolve()
    refixes_repo = args.refixes_dir.expanduser().resolve()
    assets = args.assets.expanduser().resolve() if args.assets else None
    if assets is not None and not (assets.is_dir() or assets.is_file() and zipfile.is_zipfile(assets)):
        raise VerificationError(f"Assets must be a directory or ZIP archive: {assets}")
    if args.full_boot and assets is None:
        raise VerificationError("--full-boot requires --assets")
    output = (args.output_dir or hyinit_repo / "build/local-verification" / time.strftime("%Y%m%d-%H%M%S")).expanduser().resolve()
    output.mkdir(parents=True, exist_ok=False)
    logs = output / "logs"
    logs.mkdir()
    releases = output / "artifacts"
    releases.mkdir()
    runtime_root = Path(tempfile.mkdtemp(prefix="hyinit-verify-"))
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home)
    env["PATH"] = str(java_home / "bin") + os.pathsep + env.get("PATH", "")
    for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "GRADLE_OPTS"):
        env.pop(key, None)
    result = {"status": "running", "server": str(server), "serverSha256": digest(server),
              "javaHome": str(java_home), "assets": str(assets) if assets else None,
              "runtime": str(runtime_root), "buildsSkipped": args.skip_build, "phases": {}}
    atexit.register(stop_all_processes)
    signal.signal(signal.SIGTERM, lambda *_: (stop_all_processes(), sys.exit(143)))
    current_log = None
    try:
        version_log = logs / "java-version.log"
        current_log = version_log
        run([java, "-version"], runtime_root, env, 30, version_log)
        if not re.search(r'version "(?:2[5-9]|[3-9][0-9])(?:[.\"+-])', version_log.read_text()):
            raise VerificationError("JDK 25+ is required; inspect java-version.log")
        if not args.skip_build:
            for name, repo, task in [("build-hyinit", hyinit_repo, "shadowJar"), ("build-refixes", refixes_repo, "singleJar")]:
                wrapper = file_required(repo / "gradlew", "Gradle wrapper")
                current_log = logs / (name + ".log")
                result["phases"][name] = run([
                    "sh", wrapper, "--no-daemon", "--max-workers=1", "--console=plain",
                    f"-Dorg.gradle.jvmargs=-Xmx{args.build_heap} -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2",
                    f"-PhytaleServerJar={server}", task,
                ], repo, env, args.build_timeout, current_log)
        hyinit_source = artifact(args.hyinit_jar, hyinit_repo, "hyinit")
        refixes_source = artifact(args.refixes_jar, refixes_repo, "refixes")
        hyinit = releases / hyinit_source.name
        refixes = releases / refixes_source.name
        shutil.copy2(hyinit_source, hyinit)
        shutil.copy2(refixes_source, refixes)
        result["artifacts"] = {
            "hyinit": {"path": str(hyinit), "sha256": digest(hyinit), "builtPath": str(hyinit_source)},
            "refixes": {"path": str(refixes), "sha256": digest(refixes), "builtPath": str(refixes_source)},
        }
        classes = runtime_root / "probe-classes"
        classes.mkdir()
        classpath = os.pathsep.join(map(str, [hyinit, refixes, server]))
        sources = sorted((Path(__file__).resolve().parent / "local-verification").glob("*.java"))
        if not sources:
            raise VerificationError("Missing scripts/local-verification Java helpers")
        current_log = logs / "compile-probe.log"
        result["phases"]["compile-probe"] = run([
            javac, "-J-Xmx256m", "-J-XX:ActiveProcessorCount=2", "--release", "25", "-proc:none",
            "-cp", classpath, "-d", classes, *sources,
        ], runtime_root, env, args.timeout, current_log)
        helper = releases / "local-verification-only.jar"
        package_probe(classes, helper)
        probe_classpath = os.pathsep.join([str(helper), classpath])
        java_flags = [f"-Xmx{args.server_heap}", "-Xms128m", "-Xverify:all", "-XX:ActiveProcessorCount=2",
                      f"-XX:MaxDirectMemorySize={args.direct_memory}", "-Djava.awt.headless=true",
                      "--add-opens", "java.base/sun.misc=ALL-UNNAMED",
                      "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED"]
        help_runtime = runtime_root / "launcher-help"
        help_runtime.mkdir()
        (help_runtime / "HytaleServer.jar").symlink_to(server)
        (help_runtime / "earlyplugins").mkdir()
        shutil.copy2(refixes, help_runtime / "earlyplugins" / refixes.name)
        current_log = logs / "launcher-help.log"
        result["phases"]["launcher-help"] = run([
            java, *java_flags, "-jar", hyinit, "--server-jar", server, "--accept-early-plugins", "--help",
        ], help_runtime, env, args.timeout, current_log)
        help_text = current_log.read_text(errors="replace")
        if ("--assets" not in help_text or "--auth-mode" not in help_text
                or "Exception in thread" in help_text):
            raise VerificationError("Launcher did not reach the server's real help surface")
        for mode in ("default", "all"):
            runtime = runtime_root / mode
            runtime.mkdir()
            (runtime / "earlyplugins").mkdir()
            (runtime / "HytaleServer.jar").symlink_to(server)
            shutil.copy2(refixes, runtime / "earlyplugins" / refixes.name)
            shutil.copy2(helper, runtime / "earlyplugins" / helper.name)
            current_log = logs / (mode + "-prepare.log")
            run([java, "-Xmx256m", "-XX:ActiveProcessorCount=2", "-cp", probe_classpath,
                 "local.hyinit.verify.CompatibilityProbe", hyinit, refixes, mode],
                runtime, env, args.timeout, current_log)
            current_log = logs / (mode + "-runtime.log")
            command = [java, *java_flags, "-Dmixin.debug.export=true", "-Dmixin.debug.countInjections=true",
                       "-jar", hyinit, "--server-jar", server, "--accept-early-plugins", "--disable-sentry"]
            phase_result = run(command, runtime, env, args.timeout, current_log)
            probe_result = json.loads((runtime / "verification-result.json").read_text())
            if (probe_result.get("status") != "passed" or not probe_result.get("strictInjectionCounts")):
                raise VerificationError("Probe did not finish strict transformation verification")
            phase_result["verification"] = probe_result
            result["phases"][mode] = phase_result

            if assets is not None:
                before = asset_fingerprint(assets)
                asset_args = [f"--assets={assets}", "--auth-mode=offline", f"--bind={args.bind}",
                              f"--prefab-cache={runtime / 'prefab-cache'}",
                              "--disable-cpb-build", "--disable-file-watcher"]
                current_log = logs / (mode + "-setup.log")
                setup_phase = run([java, *java_flags, "-Dmixin.debug.export=true",
                                   "-Dmixin.debug.countInjections=true", "-Dlocal.verify.setupRegression=true",
                                   "-jar", hyinit, "--server-jar", server, "--accept-early-plugins",
                                   "--disable-sentry", *asset_args],
                                  runtime, env, args.timeout, current_log)
                setup = json.loads((runtime / "setup-result.json").read_text())
                if ("completed exceptionally" not in str(setup.get("sectionBackoffDeadlock", ""))
                        or "stable UUID" not in str(setup.get("sharedInstanceUuidLessHolder", ""))):
                    raise VerificationError("Setup phase did not prove deadlock and UUID-less holder fixes")
                setup_phase["setup"] = setup
                result["phases"][mode + "-setup"] = setup_phase

                if args.full_boot:
                    current_log = logs / (mode + "-boot.log")
                    boot_phase = run([java, *java_flags, "-Dmixin.debug.export=true",
                                      "-Dmixin.debug.countInjections=true", "-Dlocal.verify.fullBoot=true",
                                      "-jar", hyinit, "--server-jar", server, "--accept-early-plugins",
                                      "--disable-sentry", *asset_args],
                                     runtime, env, args.timeout, current_log, full_boot=True,
                                     live_seconds=args.live_seconds, stop_timeout=args.stop_timeout)
                    boot = json.loads((runtime / "boot-result.json").read_text())
                    if ("completed exceptionally" not in str(boot.get("sectionBackoffDeadlock", ""))
                            or "stable UUID" not in str(boot.get("sharedInstanceUuidLessHolder", ""))):
                        raise VerificationError("Full boot did not prove deadlock and UUID-less holder fixes")
                    boot_phase["boot"] = boot
                    boot_phase["cleanStop"] = True
                    result["phases"][mode + "-boot"] = boot_phase
                if asset_fingerprint(assets) != before:
                    raise VerificationError("A run mutated the shared asset tree")
        result["status"] = "passed"
        print(f"PASS: both compatibility configurations; results and JARs: {output}")
        return 0
    except BaseException as error:
        result["status"] = "failed"
        result["error"] = str(error)
        if current_log and current_log.exists():
            print(current_log.read_text(errors="replace")[-12000:], file=sys.stderr)
        print(f"FAIL: {error}\nResults: {output}", file=sys.stderr)
        return 130 if isinstance(error, KeyboardInterrupt) else 1
    finally:
        for mode in ("default", "all", "launcher-help"):
            runtime = runtime_root / mode
            if runtime.exists():
                retain_proof(runtime, output / "runs" / mode)
        result["runtimeRetained"] = args.keep_runtime
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        if not args.keep_runtime:
            shutil.rmtree(runtime_root)
        else:
            print(f"Isolated runtime retained: {runtime_root}")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (VerificationError, OSError) as failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        sys.exit(1)
