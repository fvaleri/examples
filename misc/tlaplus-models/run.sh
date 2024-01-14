#!/usr/bin/env bash
set -Eeuo pipefail

readonly RUN_SPEC="${1:-}"
readonly RUN_CONFIG="${2:-${RUN_SPEC/.tla/}.cfg}"
readonly RUN_HOME="$HOME/.local/tlc"
readonly RUN_META="$RUN_HOME/meta"
readonly RUN_TLC_VER="v1.8.0"
readonly RUN_MOD_VER="202310082309"

for x in curl java; do
  if ! command -v "$x" &>/dev/null; then
    error "Missing required utility: $x"
  fi
done

if [[ -z $RUN_SPEC ]]; then
  echo "Usage: $0 /path/to/MySystem.tla /path/to/MySystem.cfg" && exit 1
fi

if [[ -z ${JAVA_OPTS:-} ]]; then
  JAVA_OPTS="-Xmx12g -XX:+UseParallelGC"
fi

if [[ -z ${TLC_OPTS:-} ]]; then
  TLC_OPTS="-modelcheck -metadir $RUN_META -noGenerateSpecTE "
  # disable checkpoints to speed up checking
  TLC_OPTS="$TLC_OPTS -checkpoint 0"
  # scale vertically providing n.workers = n.cores
  TLC_OPTS="$TLC_OPTS -workers auto"
  # provide enough memory for state fingerprints to stay CPU-bound
  TLC_OPTS="$TLC_OPTS -fpmem 0.80"
  # do not check for deadlock, same as CHECK_DEADLOCK = FALSE in cfg
  TLC_OPTS="$TLC_OPTS -deadlock"
fi

mkdir -p "$RUN_HOME"

if [[ ! -f $RUN_HOME/tla2tools.jar ]]; then
  echo "Downloading TLA tools $RUN_TLC_VER"
  curl https://github.com/tlaplus/tlaplus/releases/download/$RUN_TLC_VER/tla2tools.jar \
    -Lo "$RUN_HOME"/tla2tools.jar &>/dev/null
fi

if [[ ! -f $RUN_HOME/CommunityModules-deps.jar ]]; then
  echo "Downloading community modules $RUN_MOD_VER"
  curl https://github.com/tlaplus/CommunityModules/releases/download/$RUN_MOD_VER/CommunityModules-deps.jar \
    -Lo "$RUN_HOME"/CommunityModules-deps.jar &>/dev/null
fi

echo "Starting model checker"
echo "Java options: $JAVA_OPTS"
echo "TLC options: $TLC_OPTS"

# shellcheck disable=SC2086
java $JAVA_OPTS -cp $RUN_HOME/tla2tools.jar:"$RUN_HOME"/CommunityModules-deps.jar \
  tlc2.TLC $TLC_OPTS $RUN_SPEC -config $RUN_CONFIG && rm -rf "$RUN_META"
