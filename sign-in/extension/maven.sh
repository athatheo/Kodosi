#!/usr/bin/env bash
# Runs Maven on the extension in a container, with its downloads in .tools/maven of the repository.
set -euo pipefail
cd "$(dirname "$0")"
repository="$(cd ../.. && pwd)/.tools/maven"
mkdir -p "$repository"
exec docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 \
  -v "$PWD:/extension" -v "$repository:/repository" -w /extension \
  maven:3.10.0-eclipse-temurin-21@sha256:851ccd76d49d5646915645e5fb51e9b00ef53ad3dc68b0e9a2a9846b9c56bde3 \
  mvn -B -ntp -Dmaven.repo.local=/repository "$@"
