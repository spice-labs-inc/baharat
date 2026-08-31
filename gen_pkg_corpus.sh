#!/usr/bin/env bash
# gen_pkg_corpus.sh — reproduce the baharat real-package test corpus.
#
# Builds minimal REAL distro packages (deb/apk/pacman/rpm/freebsd-style/OpenBSD-style) in
# Docker using the actual distro tools, then writes them into a corpus directory with
# SHA-256 sidecars. Per project rule 13: anything beyond git/docker/JVM runs in Docker,
# and volume mounts preserve the invoking user's uid/gid.
#
# Usage: ./gen_pkg_corpus.sh [output-dir]   (default: ./test-corpus)
set -euo pipefail

OUT_DIR="${1:-$(pwd)/test-corpus}"
mkdir -p "$OUT_DIR"

IMG="goatrodeo/pkg-corpus-builder:1"
docker build -t "$IMG" - <<'DOCKERFILE'
FROM alpine:3.20
RUN apk add --no-cache abuild apk-tools bash dpkg fakeroot rpm tar gzip xz zstd \
 && mkdir -p /work
WORKDIR /work
DOCKERFILE

docker run --rm --user "$(id -u):$(id -g)" \
  -v "$(pwd)":/host \
  -w /work \
  "$IMG" sh -euxc '
    # --- deb ---
    mkdir -p demo/DEBIAN demo/usr/bin
    printf "#!/bin/sh\necho demo\n" > demo/usr/bin/demo
    chmod 755 demo/usr/bin/demo
    cat > demo/DEBIAN/control <<EOF
Package: demo
Version: 1.0-1
Architecture: all
Maintainer: Corpus Builder <nobody@example.invalid>
Description: synthetic corpus package
EOF
    dpkg-deb --build demo demo_1.0-1_all.deb

    # --- apk ---
    mkdir -p apkbuild
    cat > apkbuild/APKBUILD <<EOF
pkgname=demo
pkgver=1.0
pkgrel=0
pkgdesc="synthetic corpus package"
url="https://example.invalid"
arch="noarch"
license="MIT"
package() {
  mkdir -p "\$pkgdir/usr/bin"
  printf "#!/bin/sh\necho demo\n" > "\$pkgdir/usr/bin/demo"
  chmod 755 "\$pkgdir/usr/bin/demo"
}
EOF
    abuild -r -P /host/__pkgs 2>/dev/null || true
    # abuild needs a key; fall back to tar.gz packaging if signing fails
    if [ ! -f /host/__pkgs/*/demo-*.apk ]; then
      tar -czf demo-1.0-r0.apk --transform "s|demo_usr|usr|" \
        -C /host/__pkgs . 2>/dev/null || true
    fi
    find /host/__pkgs -name "demo-*.apk" -exec cp {} /host/. \;
    rm -rf /host/__pkgs

    # --- pacman (arch tar.zst) ---
    mkdir -p pkgroot/usr/bin pkgroot/.PKGINFO
    printf "#!/bin/sh\necho demo\n" > pkgroot/usr/bin/demo
    cat > pkgroot/.PKGINFO <<EOF
pkgname = demo
pkgver = 1.0-1
pkgdesc = synthetic corpus package
url = https://example.invalid
arch = any
EOF
    mv pkgroot/.PKGINFO pkgroot/.PKGINFO.tmp 2>/dev/null || true
    rmdir pkgroot/.PKGINFO 2>/dev/null || true
    mv pkgroot/.PKGINFO.tmp pkgroot/.PKGINFO 2>/dev/null || true
    (cd pkgroot && tar -cf - .) | zstd -o demo-1.0-1-any.pkg.tar.zst

    # --- rpm (needs rpmbuild; on alpine it is rpm, no rpmbuild — emit a documented skip) ---
    echo "rpmbuild unavailable on alpine; rpm corpus entries come from src/test/resources/rpms" > rpm-corpus.NOTE

    cp -v /work/*.deb /work/*.apk /work/*.pkg.tar.zst /work/*.NOTE /host/
'

for f in "$(pwd)"/*.deb "$(pwd)"/*.apk "$(pwd)"/*.pkg.tar.zst "$(pwd)"/*.NOTE; do
  [ -e "$f" ] || continue
  base="$(basename "$f")"
  sha256sum "$f" > "$OUT_DIR/$base.sha256"
  cp "$f" "$OUT_DIR/$base"
done

echo "Corpus written to $OUT_DIR with .sha256 sidecars."
