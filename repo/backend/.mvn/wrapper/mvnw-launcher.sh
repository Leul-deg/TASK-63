#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
BASE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
PROPS_FILE="$SCRIPT_DIR/maven-wrapper.properties"
WRAPPER_JAR="$SCRIPT_DIR/maven-wrapper.jar"

if [ ! -f "$PROPS_FILE" ]; then
  echo "Missing Maven wrapper properties: $PROPS_FILE" >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java is required to run the Maven wrapper." >&2
  exit 1
fi

download_url() {
  url="$1"
  dest="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url" -o "$dest"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$dest" "$url"
  else
    echo "Either curl or wget is required to download Maven wrapper files." >&2
    exit 1
  fi
}

wrapper_url=$(sed -n 's/^wrapperUrl=//p' "$PROPS_FILE" | tail -n 1)

if [ -z "$wrapper_url" ]; then
  echo "wrapperUrl is not configured in $PROPS_FILE" >&2
  exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven wrapper..." >&2
  download_url "$wrapper_url" "$WRAPPER_JAR"
fi

exec java \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
