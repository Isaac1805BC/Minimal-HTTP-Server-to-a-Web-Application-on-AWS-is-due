#!/usr/bin/env bash
#
# Installs the sequential HTTP server as a managed service on the EC2 instance.
# Run it ON the instance, after uploading the artifact:
#
#   sudo APP_PORT=8080 bash install-on-ec2.sh /tmp/arep-httpserver.jar
#
# It never contains credentials: the connection to the instance is made
# beforehand with Session Manager, EC2 Instance Connect or SSH.

set -euo pipefail

JAR_SOURCE="${1:-/tmp/arep-httpserver.jar}"
APP_PORT="${APP_PORT:-8080}"
APP_USER="arep"
APP_HOME="/opt/arep"
UNIT_NAME="arep-httpserver"
UNIT_SOURCE="$(dirname "$(readlink -f "$0")")/${UNIT_NAME}.service"

say() { printf '\n=== %s\n' "$1"; }

if [[ $EUID -ne 0 ]]; then
  echo "Run this script with sudo." >&2
  exit 1
fi

if [[ ! -f "$JAR_SOURCE" ]]; then
  echo "Artifact not found: $JAR_SOURCE" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
say "Installing the Java runtime (17 or newer is required)"
# ---------------------------------------------------------------------------
if command -v java >/dev/null 2>&1; then
  java -version
elif command -v dnf >/dev/null 2>&1; then          # Amazon Linux 2023, Fedora
  dnf install -y java-17-amazon-corretto-headless || dnf install -y java-17-openjdk-headless
elif command -v yum >/dev/null 2>&1; then          # Amazon Linux 2
  yum install -y java-17-amazon-corretto-headless || amazon-linux-extras install -y java-openjdk11
elif command -v apt-get >/dev/null 2>&1; then      # Ubuntu
  apt-get update -y && apt-get install -y openjdk-17-jre-headless
else
  echo "No supported package manager found. Install a Java 17+ runtime manually." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
say "Creating the service account and the application directory"
# ---------------------------------------------------------------------------
id -u "$APP_USER" >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin "$APP_USER"
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_HOME"

# ---------------------------------------------------------------------------
say "Installing the artifact"
# ---------------------------------------------------------------------------
# The public resources travel inside the jar, so this single file is the
# whole application.
install -o "$APP_USER" -g "$APP_USER" -m 0644 "$JAR_SOURCE" "${APP_HOME}/arep-httpserver.jar"

# ---------------------------------------------------------------------------
say "Installing the systemd unit on port ${APP_PORT}"
# ---------------------------------------------------------------------------
install -m 0644 "$UNIT_SOURCE" "/etc/systemd/system/${UNIT_NAME}.service"
sed -i "s/^Environment=PORT=.*/Environment=PORT=${APP_PORT}/" "/etc/systemd/system/${UNIT_NAME}.service"

# java is not always in /usr/bin on every image.
JAVA_BIN="$(command -v java)"
sed -i "s#^ExecStart=.*#ExecStart=${JAVA_BIN} -jar ${APP_HOME}/arep-httpserver.jar#" \
  "/etc/systemd/system/${UNIT_NAME}.service"

systemctl daemon-reload
systemctl enable --now "$UNIT_NAME"
systemctl restart "$UNIT_NAME"

# ---------------------------------------------------------------------------
say "Verifying the health service from inside the instance"
# ---------------------------------------------------------------------------
sleep 2
if curl -fsS "http://127.0.0.1:${APP_PORT}/app/health"; then
  printf '\n\nThe service is running. Now open it from your computer:\n'
  printf '  http://<the instance public address>:%s/\n\n' "$APP_PORT"
  printf 'Remember that the security group must allow inbound TCP on %s.\n' "$APP_PORT"
else
  echo "The health service did not answer. Inspect the log:" >&2
  echo "  sudo journalctl -u ${UNIT_NAME} -n 50 --no-pager" >&2
  exit 1
fi
