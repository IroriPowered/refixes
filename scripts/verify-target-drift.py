import argparse
import csv
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile


HELPER = "local.refixes.drift.TargetDrift"
STATUS_TEXT = {
    "missing": "missing in the current server",
    "changed": "code changed since the baseline server",
    "added": "only in the current server",
}


def arguments():
    parser = argparse.ArgumentParser(
        description="Compare the server classes and members that the Refixes mixins depend on between a baseline "
                    "and a current Hytale server JAR. Exit status 1 when a target is missing or a patched method "
                    "changed; a changed method still applies but its patch must be re-checked.")
    for name in ("java-home", "hyinit-jar", "refixes-jar", "baseline-server-jar", "server-jar"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path, help="new directory, kept for inspection")
    parser.add_argument("--timeout", type=int, default=300, help="seconds per compile or JVM (default: 300)")
    return parser.parse_args()


def run(command, cwd, env, timeout, log_path):
    with log_path.open("wb") as log:
        process = subprocess.run(list(map(str, command)), cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                 stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
    return process.returncode


def server_version(path):
    with zipfile.ZipFile(path) as jar:
        for line in jar.read("META-INF/MANIFEST.MF").decode().splitlines():
            if line.startswith("Implementation-Version:"):
                return line.split(":", 1)[1].strip()
    return path.name


def mixin_classes(refixes):
    with zipfile.ZipFile(refixes) as jar:
        manifest = json.loads(jar.read("manifest.json"))
        mixins = []
        for config_name in manifest.get("Mixins", []):
            config = json.loads(jar.read(config_name))
            mixins += [config["package"] + "." + name for name in config.get("mixins", [])]
    if not mixins:
        raise SystemExit(f"FAIL: no mixins listed in {refixes}")
    return mixins


def main():
    args = arguments()
    jars = [path.expanduser().resolve()
            for path in (args.hyinit_jar, args.refixes_jar, args.baseline_server_jar, args.server_jar)]
    for path in jars:
        if not path.is_file():
            raise SystemExit(f"FAIL: not a file: {path}")
    hyinit, refixes, baseline, server = jars
    java_home = args.java_home.expanduser().resolve()
    output = args.output_dir.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=False)
    env = os.environ.copy()
    for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
        env.pop(key, None)
    env["JAVA_HOME"] = str(java_home)

    classes = output / "classes"
    classes.mkdir()
    source = Path(__file__).resolve().parent / "local-verification" / "TargetDrift.java"
    if run([java_home / "bin/javac", "--release", "25", "-proc:none", "-cp", hyinit, "-d", classes, source],
           output, env, args.timeout, output / "compile.log") != 0:
        raise SystemExit(f"FAIL: helper compilation failed, inspect {output / 'compile.log'}")

    mixins = mixin_classes(refixes)
    table = output / "target-drift.tsv"
    log = output / "run.log"
    if run([java_home / "bin/java", "-Xmx1g", "-cp", os.pathsep.join(map(str, (classes, hyinit))), HELPER,
            refixes, baseline, server, table, *mixins], output, env, args.timeout, log) != 0:
        raise SystemExit(f"FAIL: drift helper failed, inspect {log}")
    with table.open(newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))

    before, after = server_version(baseline), server_version(server)
    findings = [row for row in rows if row["status"] != "ok"]
    counts = {status: sum(row["status"] == status for row in rows) for status in STATUS_TEXT}
    targets = {(row["mixin"], row["target"]) for row in rows}
    print(f"Mixin targets of {refixes.name}: Hytale {before} (baseline) -> {after} (current)")
    if findings:
        widths = [max(len(row[key]) for row in findings) for key in ("mixin", "kind", "status")]
        for row in findings:
            print(f"{row['mixin']:<{widths[0]}}  {row['kind']:<{widths[1]}}  {row['status']:<{widths[2]}}  "
                  f"{row['target']}  {row['member']}")
    print(f"{len(mixins)} mixins, {len(targets)} mixin targets, {len(rows)} checked dependencies: "
          f"{counts['missing']} missing, {counts['changed']} changed, {counts['added']} added; table: {table}")

    failed = counts["missing"] + counts["changed"] > 0
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        lines = [f"### Patched methods, Hytale {before} -> {after}", "",
                 f"{len(mixins)} mixins, {len(targets)} mixin targets: {counts['missing']} missing, "
                 f"{counts['changed']} changed, {counts['added']} added.", ""]
        if findings:
            lines += ["| Mixin | Target | Kind | Member | Status |", "| --- | --- | --- | --- | --- |"]
            lines += [f"| {row['mixin']} | `{row['target']}` | {row['kind']} | `{row['member']}` | "
                      f"{STATUS_TEXT[row['status']]} |" for row in findings]
            lines.append("")
        with open(summary_path, "a") as summary:
            summary.write("\n".join(lines) + "\n")

    if failed:
        print(f"FAIL: re-check every mixin listed as missing or changed against Hytale {after}; "
              f"a changed method still applies but its patch may no longer be correct", file=sys.stderr)
        return 1
    print(f"PASS: every mixin target and patched method is unchanged since Hytale {before}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except subprocess.TimeoutExpired as error:
        print(f"FAIL: timed out: {error}", file=sys.stderr)
        sys.exit(1)
