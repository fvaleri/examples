#!/usr/bin/env bash
set -Eeuo pipefail
error() { echo -e "$@" && exit 1; }
for x in javac native-image upx docker gcc; do
  if ! command -v "$x" &>/dev/null; then
    error "Missing required utility: $x"
  fi
done

export JAVA_HOME="$HOME/.local/graalvm"
export TOOLCHAIN_DIR="$HOME/.local/musl"
export PATH="$TOOLCHAIN_DIR/bin:$JAVA_HOME/bin:$PATH"
export CC="$TOOLCHAIN_DIR/bin/gcc"

rm -rf build
mkdir -p build

# compile java source file
javac src/Hello.java -d build

# compile Java bytecodes into a fully statically linked executable
native-image --static --libc=musl Hello -o build/hello --class-path build

# create a compressed version of the executable
rm -f hello.upx
upx --lzma --best build/hello -o build/hello.upx

# package the compressed executable in a simple scratch image
docker build . -t hello:upx
