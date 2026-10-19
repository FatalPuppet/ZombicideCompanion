#!/bin/sh
# Lightweight bootstrap launcher for this project. Uses the exact version pinned in gradle-wrapper.properties.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VERSION=$(sed -n 's#.*gradle-\([0-9][0-9.]*\)-bin\.zip.*#\1#p' "$APP_HOME/gradle/wrapper/gradle-wrapper.properties")
if [ -z "$VERSION" ]; then echo "Could not read Gradle version from gradle-wrapper.properties" >&2; exit 1; fi
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/zombicide-gradle/gradle-$VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/zombicide-gradle"
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then wget "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -O "$ZIP"
    else echo "Install curl or wget to download Gradle $VERSION." >&2; exit 1; fi
  fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$GRADLE_HOME/bin/gradle" -p "$APP_HOME" "$@"
