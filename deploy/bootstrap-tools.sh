#!/usr/bin/env bash
# Install isolated build/runtime tools without changing the system Java.
set -euo pipefail
TOOLS_DIR="${TOOLS_DIR:-/opt/febs-tools}"
mkdir -p "$TOOLS_DIR"
if [ ! -x "$TOOLS_DIR/jdk8/bin/java" ]; then
  tool_tmp=$(mktemp -d)
  trap 'rm -rf "$tool_tmp"' EXIT
  # Official Corretto distribution and SHA-256 manifest.
  curl -fsSL --connect-timeout 15 --max-time 600 --retry 3 https://corretto.aws/downloads/latest/amazon-corretto-8-x64-linux-jdk.tar.gz -o "$tool_tmp/jdk.tar.gz"
  curl -fsSL --connect-timeout 15 --max-time 60 --retry 3 https://corretto.aws/downloads/latest_sha256/amazon-corretto-8-x64-linux-jdk.tar.gz -o "$tool_tmp/jdk.sha256"
  echo "$(awk '{print $1}' "$tool_tmp/jdk.sha256")  $tool_tmp/jdk.tar.gz" | sha256sum -c -
  mkdir -p "$TOOLS_DIR/jdk8"
  tar -xzf "$tool_tmp/jdk.tar.gz" -C "$TOOLS_DIR/jdk8" --strip-components=1
fi
if [ ! -x "$TOOLS_DIR/apache-maven-3.9.9/bin/mvn" ]; then
  tool_tmp=${tool_tmp:-$(mktemp -d)}
  trap 'rm -rf "$tool_tmp"' EXIT
  base=https://maven.aliyun.com/repository/central/org/apache/maven/apache-maven/3.9.9
  curl -fsSL --connect-timeout 15 --max-time 180 --retry 2 "$base/apache-maven-3.9.9-bin.tar.gz" -o "$tool_tmp/maven.tar.gz"
  curl -fsSL --connect-timeout 15 --max-time 180 --retry 2 "$base/apache-maven-3.9.9-bin.tar.gz.sha512" -o "$tool_tmp/maven.sha512"
  echo "$(cat "$tool_tmp/maven.sha512")  $tool_tmp/maven.tar.gz" | sha512sum -c -
  tar -xzf "$tool_tmp/maven.tar.gz" -C "$TOOLS_DIR"
fi
"$TOOLS_DIR/jdk8/bin/java" -version
JAVA_HOME="$TOOLS_DIR/jdk8" "$TOOLS_DIR/apache-maven-3.9.9/bin/mvn" -version
