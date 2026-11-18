#!/usr/bin/env bash
# DupeCheck server setup for a fresh Ubuntu 24.04 GCE VM.
# Run from the folder where dupecheck-server.tar.gz was extracted:  sudo bash setup.sh
# Safe to run again (re-copies code, keeps data and /etc/dupecheck.env).
set -euo pipefail

APP_DIR=/opt/dupecheck
DATA_DIR=/var/lib/dupecheck
ENV_FILE=/etc/dupecheck.env
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
GOOGLE_CLIENT_ID=599165364681-s9frbpm05o8nko732c27715j557fuvni.apps.googleusercontent.com

echo "== 1/7 packages"
apt-get update -y
apt-get install -y python3-venv python3-pip caddy curl

echo "== 2/7 swap (2GB, helps torch install on 4GB RAM)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 3/7 user and code"
id dupecheck >/dev/null 2>&1 || useradd --system --home "$DATA_DIR" --shell /usr/sbin/nologin dupecheck
mkdir -p "$APP_DIR" "$DATA_DIR"
rm -rf "$APP_DIR/sandbox"
cp -r "$SRC_DIR/sandbox" "$APP_DIR/"
chown -R dupecheck:dupecheck "$DATA_DIR"

echo "== 4/7 python packages (torch CPU, takes a few minutes)"
[ -d "$APP_DIR/venv" ] || python3 -m venv "$APP_DIR/venv"
PIP="$APP_DIR/venv/bin/pip"
"$PIP" install --upgrade pip
"$PIP" install -r "$APP_DIR/sandbox/mmminsung02/src/server/requirements.txt"
"$PIP" install torch==2.15.0 --index-url https://download.pytorch.org/whl/cpu
"$PIP" install transformers==5.22.1 anthropic

echo "== 5/7 env file"
if [ ! -f "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<EOF
DATA_DIR=$DATA_DIR
HF_HOME=$DATA_DIR/hf
GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID
# Optional: Claude features (translation, AI clerk, report text, screenshot reading, product LLM)
ANTHROPIC_API_KEY=
EOF
  chmod 600 "$ENV_FILE"
fi

echo "== 6/7 systemd service"
cat > /etc/systemd/system/dupecheck.service <<EOF
[Unit]
Description=DupeCheck API server
After=network-online.target
Wants=network-online.target

[Service]
User=dupecheck
EnvironmentFile=$ENV_FILE
WorkingDirectory=$APP_DIR/sandbox/mmminsung02/src
ExecStart=$APP_DIR/venv/bin/uvicorn server.main:create_app --factory --host 127.0.0.1 --port 8000
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable dupecheck
systemctl restart dupecheck

echo "== 7/7 HTTPS (Caddy + sslip.io)"
IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip)
DOMAIN="${IP//./-}.sslip.io"
cat > /etc/caddy/Caddyfile <<EOF
$DOMAIN {
    request_body {
        max_size 12MB
    }
    reverse_proxy 127.0.0.1:8000
}
EOF
systemctl restart caddy

echo "Waiting for the server (first start downloads the CLIP model)..."
for i in $(seq 1 60); do
  if curl -fs "https://$DOMAIN/health" >/dev/null 2>&1; then
    echo
    echo "OK  https://$DOMAIN/health -> $(curl -s "https://$DOMAIN/health")"
    echo "SERVER URL: https://$DOMAIN/"
    exit 0
  fi
  sleep 5; printf .
done
echo
echo "Not up yet. Check:  sudo journalctl -u dupecheck -n 50   and   sudo journalctl -u caddy -n 50"
echo "Expected URL: https://$DOMAIN/"
exit 1
