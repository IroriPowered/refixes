#!/usr/bin/env bash
set -euo pipefail

target=${1:?usage: fetch-hyinit.sh <output JAR>}
properties="$(dirname "$0")/../gradle.properties"
version=$(sed -n 's/^hyinit_version *= *//p' "$properties")
sha256=$(sed -n 's/^hyinit_sha256 *= *//p' "$properties")
if [ -z "$version" ] || [ -z "$sha256" ]; then
  echo "gradle.properties needs hyinit_version and hyinit_sha256" >&2
  exit 1
fi
url="https://github.com/IroriPowered/hyinit/releases/download/$version/Hyinit-$version.jar"
if ! curl -fsSL -o "$target" "$url"; then
  echo "Hyinit $version is not available at $url. Publish that release first, or build Hyinit yourself and pass -PhyinitJar=<path>." >&2
  exit 1
fi
actual=$(sha256sum "$target" | cut -d ' ' -f 1)
if [ "$actual" != "$sha256" ]; then
  rm -f "$target"
  echo "Hyinit $version from $url has sha256 $actual, but gradle.properties pins hyinit_sha256 $sha256." >&2
  exit 1
fi
echo "Fetched Hyinit $version with sha256 $sha256"
