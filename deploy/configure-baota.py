#!/usr/bin/env python3
"""Configure this project's existing Baota Tomcat/Nginx instance.

Deployment backs up files first. Secrets remain outside the Git repository.
"""
import os
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

repo = Path(__file__).resolve().parents[1]
tomcat = Path(os.environ.get('TOMCAT_HOME', '/usr/local/bttomcat/tomcat9'))
tools = Path(os.environ.get('TOOLS_DIR', '/opt/febs-tools'))
backup = Path(os.environ['DEPLOY_BACKUP'])
backup.mkdir(parents=True, exist_ok=True)
env_dir = Path('/etc/febs')
env_dir.mkdir(mode=0o700, exist_ok=True)
runtime = env_dir / 'runtime.env'
setenv = tomcat / 'bin/setenv.sh'
if not runtime.exists():
    if not setenv.exists():
        raise SystemExit('Configure /etc/febs/runtime.env with DB credentials first')
    shutil.copy2(setenv, runtime)
    # Keep existing credentials; use local services and durable data directories.
    with runtime.open('a') as f:
        f.write('\nexport DB_HOST=127.0.0.1\nexport REDIS_HOST=127.0.0.1\n')
        f.write('export UPLOAD_PATH=/var/lib/febs/upload\nexport LOG_PATH=/var/log/febs\n')
    runtime.chmod(0o600)

for name in ('upload', 'webapps'):
    path = Path('/var/lib/febs') / name
    path.mkdir(parents=True, exist_ok=True)
    shutil.chown(path, user='www', group='www')
logs = Path('/var/log/febs')
logs.mkdir(parents=True, exist_ok=True)
shutil.chown(logs, user='www', group='www')

# Back up the old startup configuration, including credentials (root-only).
backup.chmod(0o700)
if setenv.exists():
    shutil.copy2(setenv, backup / 'setenv.sh')
setenv.write_text('#!/bin/sh\n. /etc/febs/runtime.env\n'
                  f'export JAVA_HOME="{tools}/jdk8"\n'
                  'export SPRING_PROFILES_ACTIVE=prod\n'
                  'export SERVICE_START_WAIT_TIME=120\n'
                  'export JAVA_OPTS="-Xms256m -Xmx1024m -Xss512k -Dfile.encoding=UTF-8 -Djava.awt.headless=true"\n')
setenv.chmod(0o700)

server = tomcat / 'conf/server.xml'
shutil.copy2(server, backup / 'server.xml')
tree = ET.parse(server)
engine = tree.find('./Service/Engine')
host = next((h for h in engine.findall('Host') if h.get('name') == '81.68.239.216'), None)
if host is None:
    raise SystemExit('Expected Baota host 81.68.239.216 was not found')
engine.set('defaultHost', '81.68.239.216')
host.set('appBase', '/var/lib/febs/webapps')
host.set('unpackWARs', 'true')
for context in list(host.findall('Context')):
    if 'febs' in context.get('docBase', ''):
        host.remove(context)
for attribute in ('xmlNamespaceAware', 'xmlValidation'):
    host.attrib.pop(attribute, None)
tree.write(server, encoding='utf-8', xml_declaration=True)

nginx = Path('/www/server/panel/vhost/nginx/java_febs.conf')
shutil.copy2(nginx, backup / 'java_febs.conf')
# Keep Baota extension/certificate/statistics configuration in place.
nginx.write_text('''server {
    listen 80;
    server_name 81.68.239.216;
    root /myproject/bottled_water_sales_cos/frontend/dist;
    index index.html;
    include /www/server/panel/vhost/nginx/extension/febs/*.conf;
    include /www/server/panel/vhost/nginx/well-known/febs.conf;
    client_max_body_size 100m;
    location = /febs { return 301 /febs/; }
    location /febs/ {
        proxy_pass http://127.0.0.1:8233;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_read_timeout 300s;
    }
    location /imagesWeb/ {
        proxy_pass http://127.0.0.1:8233/febs/imagesWeb/;
        proxy_set_header Host $host;
    }
    location /.well-known/ { root /www/wwwroot/java_node_ssl; }
    location ~ /\\. { deny all; }
    location / { try_files $uri $uri/ /index.html; }
    access_log /www/wwwlogs/febs.log;
    error_log /www/wwwlogs/febs.error.log;
}
''')
print('Configured /febs on Tomcat 8233; secrets remain in /etc/febs/runtime.env')
