#!/usr/bin/env bash
# After git pull --ff-only, run this as root on the existing Baota server.
set -euo pipefail
REPO_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
export TOMCAT_HOME="${TOMCAT_HOME:-/usr/local/bttomcat/tomcat9}"
export TOOLS_DIR="${TOOLS_DIR:-/opt/febs-tools}"
export DEPLOY_BACKUP="/var/backups/febs/$(date +%Y%m%d%H%M%S)"
export SERVICE_START_WAIT_TIME=120
SERVICE=/etc/init.d/bttomcat9
NGINX=/www/server/nginx/sbin/nginx
APP_BASE=/var/lib/febs/webapps
[ "$(id -u)" = 0 ] || { echo 'Run as root'; exit 1; }
[ -x "$SERVICE" ] && [ -f "$TOMCAT_HOME/conf/server.xml" ] || { echo 'Expected Baota Tomcat9 not found'; exit 1; }
exec 9>/var/lock/febs-deploy.lock
flock -n 9 || { echo 'Another deployment is running'; exit 1; }
bash "$REPO_DIR/deploy/bootstrap-tools.sh"
export JAVA_HOME="$TOOLS_DIR/jdk8"
export PATH="$JAVA_HOME/bin:$TOOLS_DIR/apache-maven-3.9.9/bin:$PATH"
# Build and test before interrupting the running service.
cd "$REPO_DIR/backend"
mvn -B clean verify
[ -s target/febs.war ]
[ -f "$REPO_DIR/frontend/dist/index.html" ] || { echo 'Build frontend/dist before deploying'; exit 1; }
mkdir -p "$DEPLOY_BACKUP"
chmod 700 "$DEPLOY_BACKUP"
# Save the existing application for rollback, including the expanded directory.
if [ -f "$APP_BASE/febs.war" ]; then
  cp -p "$APP_BASE/febs.war" "$DEPLOY_BACKUP/febs.war"
fi
rollback() {
  echo "Deployment failed; restoring backup: $DEPLOY_BACKUP" >&2
  "$SERVICE" stop || true
  for item in server.xml setenv.sh; do
    case "$item" in
      server.xml) destination="$TOMCAT_HOME/conf/server.xml" ;;
      setenv.sh) destination="$TOMCAT_HOME/bin/setenv.sh" ;;
    esac
    [ ! -f "$DEPLOY_BACKUP/$item" ] || cp -p "$DEPLOY_BACKUP/$item" "$destination"
  done
  if [ -f "$DEPLOY_BACKUP/java_febs.conf" ]; then
    cp -p "$DEPLOY_BACKUP/java_febs.conf" /www/server/panel/vhost/nginx/java_febs.conf
  fi
  if [ -f "$APP_BASE/febs.war" ]; then mv "$APP_BASE/febs.war" "$DEPLOY_BACKUP/failed.war"; fi
  if [ -d "$APP_BASE/febs" ]; then mv "$APP_BASE/febs" "$DEPLOY_BACKUP/failed-expanded"; fi
  if [ -f "$DEPLOY_BACKUP/febs.war" ]; then cp -p "$DEPLOY_BACKUP/febs.war" "$APP_BASE/febs.war"; fi
  if [ -d "$DEPLOY_BACKUP/expanded" ]; then mv "$DEPLOY_BACKUP/expanded" "$APP_BASE/febs"; fi
  "$SERVICE" start || true
  "$NGINX" -t && "$NGINX" -s reload || true
}
"$SERVICE" stop
trap rollback ERR
python3 "$REPO_DIR/deploy/configure-baota.py"
"$NGINX" -t
if [ -d "$APP_BASE/febs" ]; then mv "$APP_BASE/febs" "$DEPLOY_BACKUP/expanded"; fi
install -o www -g www -m 644 target/febs.war "$APP_BASE/febs.war"
"$SERVICE" start
healthy=false
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 5 http://127.0.0.1:8233/febs/actuator/health > "$DEPLOY_BACKUP/health.json" 2>/dev/null; then
    if python3 -c 'import json,sys; sys.exit(json.load(open(sys.argv[1])).get("status") != "UP")' "$DEPLOY_BACKUP/health.json"; then
      healthy=true
      break
    fi
  fi
  sleep 2
done
if [ "$healthy" != true ]; then
  echo 'Health check failed. Inspect /www/wwwlogs/neizhi9/catalina-daemon.out and /var/log/febs' >&2
  false
fi
"$NGINX" -s reload
curl -fsS --max-time 10 -H 'Host: 81.68.239.216' http://127.0.0.1/febs/actuator/health
trap - ERR
printf '\nDeployment successful. Revision: '
git -C "$REPO_DIR" rev-parse --short HEAD
printf 'Backup: %s\n' "$DEPLOY_BACKUP"
