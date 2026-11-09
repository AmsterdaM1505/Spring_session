#!/usr/bin/env sh
set -eu
PROJECT_ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
MAVEN_HOME="$PROJECT_ROOT/.tools/apache-maven-3.9.9"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$PROJECT_ROOT/.tools"
  artifact='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz'
  curl -fsSL "$artifact" -o "$PROJECT_ROOT/.tools/maven.tar.gz"
  expected=$(curl -fsSL "$artifact.sha512" | awk '{print $1}')
  printf '%s  %s\n' "$expected" "$PROJECT_ROOT/.tools/maven.tar.gz" | sha512sum -c -
  tar -xzf "$PROJECT_ROOT/.tools/maven.tar.gz" -C "$PROJECT_ROOT/.tools"
fi
cd "$PROJECT_ROOT"
exec "$MAVEN_HOME/bin/mvn" "$@"
