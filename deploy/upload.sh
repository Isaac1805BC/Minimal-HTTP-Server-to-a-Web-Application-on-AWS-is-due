#!/usr/bin/env bash
#
# Copies the artifact and the installation files to the EC2 instance.
# Run it on YOUR computer, from the repository root:
#
#   ./deploy/upload.sh ec2-user@<public address> ~/.ssh/<your key>.pem
#
# The key path is an argument on purpose: no key, address or credential is
# ever stored in this repository.

set -euo pipefail

TARGET="${1:-}"
KEY="${2:-}"

if [[ -z "$TARGET" ]]; then
  echo "Usage: $0 <user>@<public address> [path to the .pem key]" >&2
  exit 1
fi

JAR="target/arep-httpserver.jar"
if [[ ! -f "$JAR" ]]; then
  echo "The artifact does not exist yet. Build it first: mvn clean package" >&2
  exit 1
fi

SSH_OPTIONS=()
if [[ -n "$KEY" ]]; then
  chmod 400 "$KEY"
  SSH_OPTIONS=(-i "$KEY")
fi

echo "==> Copying the artifact and the installation files to ${TARGET}"
scp "${SSH_OPTIONS[@]}" \
  "$JAR" \
  deploy/install-on-ec2.sh \
  deploy/arep-httpserver.service \
  "${TARGET}:/tmp/"

echo
echo "==> Now connect and install:"
echo "    ssh ${SSH_OPTIONS[*]} ${TARGET}"
echo "    sudo APP_PORT=8080 bash /tmp/install-on-ec2.sh /tmp/arep-httpserver.jar"
