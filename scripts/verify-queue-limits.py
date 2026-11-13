import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile


CONFIG = {"Early": {"OutgoingQueueMaxBytes": 8192, "OutgoingQueueMaxPackets": 0}}


def arguments():
    parser = argparse.ArgumentParser(
        description="Verify the outgoing queue limits of an already-built Refixes JAR in one asset-free Hyinit JVM.")
    parser.add_argument("--hyinit-jar", required=True, type=Path)
    parser.add_argument("--refixes-jar", required=True, type=Path)
    parser.add_argument("--server-jar", required=True, type=Path)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path, help="new directory, kept for inspection")
    parser.add_argument("--timeout", type=int, default=120, help="seconds per compile or JVM (default: 120)")
    return parser.parse_args()


def run(command, cwd, env, timeout, log_path):
    with log_path.open("wb") as log:
        process = subprocess.run(list(map(str, command)), cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                 stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
    return process.returncode


def main():
    args = arguments()
    jars = [path.expanduser().resolve() for path in (args.hyinit_jar, args.refixes_jar, args.server_jar)]
    for path in jars:
        if not path.is_file():
            raise SystemExit(f"FAIL: not a file: {path}")
    hyinit, refixes, server = jars
    java_home = args.java_home.expanduser().resolve()
    output = args.output_dir.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=False)
    env = os.environ.copy()
    for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
        env.pop(key, None)
    env["JAVA_HOME"] = str(java_home)

    classes = output / "classes"
    classes.mkdir()
    sources = Path(__file__).resolve().parent / "local-verification"
    if run([java_home / "bin/javac", "--release", "25", "-proc:none",
            "-cp", os.pathsep.join(map(str, jars)), "-d", classes,
            sources / "QueueLimitsRegression.java", sources / "MixinQueueLimitsMain.java"],
           output, env, args.timeout, output / "compile.log") != 0:
        raise SystemExit(f"FAIL: fixture compilation failed, inspect {output / 'compile.log'}")

    runtime = output / "runtime"
    plugins = runtime / "earlyplugins"
    plugins.mkdir(parents=True)
    (runtime / "HytaleServer.jar").symlink_to(server)
    shutil.copy2(refixes, plugins / "Refixes.jar")
    with zipfile.ZipFile(plugins / "queue-limits-verification.jar", "w") as jar:
        for path in sorted(classes.rglob("*.class")):
            jar.write(path, path.relative_to(classes).as_posix())
        jar.writestr("manifest.json", json.dumps({
            "Group": "LocalVerification", "Name": "QueueLimitsRegression", "Version": "1.0.0",
            "Mixins": ["queue-limits-verification.mixins.json"],
        }))
        jar.writestr("queue-limits-verification.mixins.json", json.dumps({
            "required": True, "minVersion": "0.8", "package": "local.hyinit.verify.mixin",
            "mixins": ["MixinQueueLimitsMain"], "injectors": {"defaultRequire": 1},
        }))
    config_directory = runtime / "mods" / "IroriPowered_Refixes"
    config_directory.mkdir(parents=True)
    (config_directory / "Refixes.json").write_text(json.dumps({"AllowUnsupportedServerVersion": True}) + "\n")
    (config_directory / "config.json").write_text(json.dumps(CONFIG) + "\n")

    log = output / "run.log"
    exit_code = run([java_home / "bin/java", "-Xmx1g", "-XX:MaxDirectMemorySize=256m",
                     "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                     "-Djava.awt.headless=true", "-jar", hyinit, "--server-jar", server,
                     "--accept-early-plugins", "--disable-sentry"], runtime, env, args.timeout, log)
    result_path = runtime / "queue-limits-result.json"
    result = json.loads(result_path.read_text()) if result_path.is_file() else None
    if exit_code != 0 or result is None or result.get("status") != "passed":
        raise SystemExit(f"FAIL: queue limit regression failed, inspect {log} and {result_path}")
    print(f"PASS: config table, Netty and Quiche byte boundaries, close-path exemption: {output}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except subprocess.TimeoutExpired as error:
        print(f"FAIL: timed out: {error}", file=sys.stderr)
        sys.exit(1)
