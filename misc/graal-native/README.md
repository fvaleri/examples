```sh
# install OS deps and get the musl toolchain
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static upx
curl -Ls https://more.musl.cc/10.2.1/x86_64-linux-musl/x86_64-linux-musl-native.tgz | tar -C $HOME/.local -xzf -
mv $HOME/.local/x86_64-linux-musl-native $HOME/.local/musl
export JAVA_HOME="$HOME/.local/graalvm"
export TOOLCHAIN_DIR="$HOME/.local/musl"
export PATH="$TOOLCHAIN_DIR/bin:$JAVA_HOME/bin:$PATH"
export CC="$TOOLCHAIN_DIR/bin/gcc"

# install native image and zlib into the toolchain
git clone git@github.com:madler/zlib.git
cd zlib
./configure --prefix="$TOOLCHAIN_DIR" --static
make
make install

# build
./build.sh
ls -lh build
docker images

# test
time java Hello
time ./hello
time ./hello.upx
time docker run --rm hello:upx
```
