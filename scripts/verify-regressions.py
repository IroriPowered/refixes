import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import sys
import time


def positive_seconds(value):
    value = int(value)
    if value <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return value


def stop_process(process):
    for action in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(process.pid, action)
        except ProcessLookupError:
            break
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            pass
    process.wait()


def terminated(*_):
    raise InterruptedError("received SIGTERM")


def main():
    parser = argparse.ArgumentParser(
        description="Run controlled source regressions and built-JAR wrapper checks with JDK 25. "
                    "Use verify-local.py separately for actual Hyinit transformations and native probes.")
    for name in ("java-home", "hyinit-jar", "refixes-jar", "server-jar", "output-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--timeout", type=positive_seconds, default=60,
                        help="seconds per compilation or regression process (default: 60)")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    output = args.output_dir.expanduser().resolve()
    try:
        output.mkdir(parents=True, exist_ok=False)
    except OSError as failure:
        parser.error(f"output directory must be new: {failure}")
    logs, runtime, controlled, wrappers = [output / name for name in ("logs", "runtime", "controlled", "wrappers")]
    for directory in (logs, runtime, controlled, wrappers):
        directory.mkdir()
    result = {"status": "running", "checks": []}
    java_home = args.java_home.expanduser().resolve()
    java, javac = [java_home / "bin" / name for name in ("java", "javac")]
    jars = [path.expanduser().resolve() for path in (args.hyinit_jar, args.refixes_jar, args.server_jar)]
    env = dict(os.environ, JAVA_HOME=str(java_home))
    env["PATH"] = str(java_home / "bin") + os.pathsep + env.get("PATH", "")
    for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "GRADLE_OPTS"):
        env.pop(key, None)

    def save_result():
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")

    def run(name, command, markers=(), pattern=None):
        command = list(map(str, command))
        log_path = logs / (name + ".log")
        working_directory = runtime / name
        working_directory.mkdir()
        check = {"name": name, "command": command, "log": str(log_path),
                 "workingDirectory": str(working_directory), "status": "running"}
        result["checks"].append(check)
        started = time.monotonic()
        print(f"[{name}] {shlex.join(command)}", flush=True)
        try:
            with log_path.open("wb") as log:
                process = subprocess.Popen(command, cwd=working_directory, env=env, stdin=subprocess.DEVNULL,
                                           stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                try:
                    check["exitCode"] = process.wait(timeout=args.timeout)
                except BaseException:
                    stop_process(process)
                    raise
            if check["exitCode"] != 0:
                raise RuntimeError(f"{name} exited {check['exitCode']}; inspect {log_path}")
            text = log_path.read_text(errors="replace")
            if any(marker not in text for marker in markers) or (pattern and not re.search(pattern, text)):
                raise RuntimeError(f"{name} did not report the expected result; inspect {log_path}")
            check["status"] = "passed"
        except BaseException as failure:
            check.update(status="failed", error=str(failure) or type(failure).__name__)
            raise
        finally:
            check["seconds"] = round(time.monotonic() - started, 2)
            save_result()

    signal.signal(signal.SIGTERM, terminated)
    try:
        for path in (java, javac, *jars):
            if not path.is_file():
                raise RuntimeError(f"required file is missing: {path}")
        result["javaHome"] = str(java_home)
        result["artifacts"] = {}
        for name, path in zip(("hyinit", "refixes", "server"), jars):
            with path.open("rb") as stream:
                result["artifacts"][name] = {"path": str(path), "sha256": hashlib.file_digest(stream, "sha256").hexdigest()}
        run("java-version", [java, "-version"], pattern=r'version "25(?:[.\"+\-])')
        run("javac-version", [javac, "-version"], pattern=r'\bjavac 25(?:[.\s+\-]|$)')
        fixtures = repo / "scripts/local-verification"
        service_tests = (
            ("CoalescingPersistenceRegression", (), "CoalescingPersistenceRegression passed"),
            ("OwnedKeepLoadedRegression", (), "OwnedKeepLoadedRegression passed"),
            ("ChunkLoaderLifecycleRegression", (), "ChunkLoaderLifecycleRegression passed"),
            ("ChunkLoaderCallbackShutdownRegression", (), "ChunkLoaderCallbackShutdownRegression PASS"),
            ("PersistenceShutdownRegression", (), "ALL PASS"),
            ("PersistenceShutdownRegression", ("service",), "ALL PASS"),
        )
        source = repo / "plugin/src/main/java/cc/irori/refixes"
        sources = sorted((fixtures / "sections-stubs").rglob("*.java"))
        sources += [source / "service" / (name + ".java") for name in ("ChunkLoaderService", "CoalescingPersistence", "OwnedKeepLoaded")]
        sources += sorted({fixtures / (name + ".java") for name, _, _ in service_tests})
        for path in sources:
            if not path.is_file():
                raise RuntimeError(f"required fixture or source is missing: {path}")
        classpath = os.pathsep.join(map(str, jars))
        controlled_cp = str(controlled) + os.pathsep + classpath
        compiler = [javac, "-J-Xmx256m", "-J-XX:ActiveProcessorCount=2", "--release", "25", "-proc:none"]
        jvm = [java, "-Xverify:all", "-Xmx256m", "-XX:ActiveProcessorCount=2",
               "-Djava.util.concurrent.ForkJoinPool.common.parallelism=2"]
        run("controlled-compile", [*compiler, "-cp", controlled_cp, "-d", controlled, *sources])
        for name, test_args, marker in service_tests:
            run("-".join((name, *test_args)), [*jvm, "-cp", controlled_cp, "cc.irori.refixes.service." + name, *test_args],
                markers=(marker,))
        wrapper_cp = str(wrappers) + os.pathsep + classpath
        run("wrapper-compile", [*compiler, "-cp", classpath, "-d", wrappers, fixtures / "WrapperRegression.java"])
        for mode, marker in (("wrappers", "PASS: original-operation composition,"), ("capability", "PASS: Fabric capability supported,")):
            run(mode, [*jvm, "-cp", wrapper_cp, "local.hyinit.verify.WrapperRegression", mode], markers=(marker,))
        policy_tests = (
            ("StoreShutdownRegression", "local.hyinit.verify", "StoreShutdownRegression PASS"),
            ("PolicyMixinConfigRegression", "local.refixes.policy", "PASS early mixin policy configuration regressions"),
            ("RuntimeConfigurationRegression", "cc.irori.refixes.config.impl", "RuntimeConfigurationRegression passed"),
            ("ConfigMigrationRegression", "cc.irori.refixes.config.impl", "ConfigMigrationRegression passed"),
            ("WatchdogPolicyRegression", "cc.irori.refixes.service", "WatchdogPolicyRegression passed"),
            ("SharedInstancePersistenceRegression", "cc.irori.refixes.system", "SharedInstancePersistenceRegression passed"),
            ("WorldPolicyRegression", "local.hyinit.verify", "WorldPolicyRegression PASS"),
            ("WorldPolicyTransferRegression", "local.hyinit.verify", "WorldPolicyTransferRegression PASS"),
            ("RecoveryPolicyRegression", "local.hyinit.verify", "RecoveryPolicyRegression PASS"),
            ("LightPolicyRegression", "cc.irori.refixes.early.mixin", "LightPolicyRegression PASS"),
            ("RepulsionPolicyRegression", "cc.irori.refixes.early.mixin", "RepulsionPolicyRegression PASS"),
            ("RepulsionNativePolicyRegression", "cc.irori.refixes.early.mixin", "RepulsionNativePolicyRegression PASS"),
        )
        run("policy-compile", [*compiler, "-cp", classpath, "-d", wrappers,
                               *(fixtures / (name + ".java") for name, _, _ in policy_tests)])
        for name, package, marker in policy_tests:
            run(name, [*jvm, "-cp", wrapper_cp, package + "." + name], markers=(marker,))
        result["status"] = "passed"
        print(f"PASS focused regressions: {output / 'result.json'}", flush=True)
        return 0
    except (Exception, KeyboardInterrupt) as failure:
        result.update(status="failed", error=str(failure) or type(failure).__name__)
        print(f"FAIL: {result['error']}", file=sys.stderr, flush=True)
        return 1
    finally:
        save_result()


if __name__ == "__main__":
    sys.exit(main())
