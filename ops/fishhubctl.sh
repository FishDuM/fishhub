#!/bin/zsh
set -euo pipefail

project_root=${0:A:h:h}
user_home=$(dscl . -read "/Users/$(id -un)" NFSHomeDirectory | /usr/bin/awk '{print $2}')
launch_agents="$user_home/Library/LaunchAgents"
runtime_root="$user_home/Library/Application Support/FishHub"
uid=$(id -u)
services=(xxl-job-admin id kv oss count search user note relation comment auth data-align gateway)
base_services=(xxl-job-admin id kv oss count search)
business_services=(user note relation comment auth data-align)
reverse_services=(gateway data-align auth comment relation note user search count oss kv id xxl-job-admin)

usage() {
  print "用法: $0 {install|start|stop|restart|status}"
}

install() {
  stop_all
  deploy
  mkdir -p "$launch_agents"
  for service in "${services[@]}"; do
    sed -e "s|__RUNTIME_ROOT__|$runtime_root|g" -e "s|__RELEASE_ROOT__|$runtime_root/current|g" -e "s|__SERVICE__|$service|g" \
      "$project_root/ops/com.fishhub.service.plist.template" > "$launch_agents/com.fishhub.$service.plist"
    plutil -lint "$launch_agents/com.fishhub.$service.plist" >/dev/null
  done
}

deploy() {
  local release_id release_root
  release_id=$(date +%Y%m%d%H%M%S)
  release_root="$runtime_root/releases/$release_id"
  mkdir -p "$release_root/jars" "$release_root/ops" "$runtime_root/logs"
  cp "$project_root/ops/fishhub-service.sh" "$release_root/ops/fishhub-service.sh"
  chmod +x "$release_root/ops/fishhub-service.sh"
  local service source
  for service in "${services[@]}"; do
    case "$service" in
      xxl-job-admin) source="xxl-job/xxl-job-admin/target/xxl-job-admin-2.4.1.jar" ;;
      id) source="fishhub-distributed-id-generator/fishhub-distributed-id-generator-biz/target/fishhub-distributed-id-generator-biz-0.0.1-SNAPSHOT.jar" ;;
      kv) source="fishhub-kv/fishhub-kv-biz/target/fishhub-kv-biz-0.0.1-SNAPSHOT.jar" ;;
      oss) source="fishhub-oss/fishhub-oss-biz/target/fishhub-oss-biz-0.0.1-SNAPSHOT.jar" ;;
      count) source="fishhub-count/fishhub-count-biz/target/fishhub-count-biz-0.0.1-SNAPSHOT.jar" ;;
      search) source="fishhub-search/fishhub-search-biz/target/fishhub-search-biz-0.0.1-SNAPSHOT.jar" ;;
      user) source="fishhub-user/fishhub-user-biz/target/fishhub-user-biz-0.0.1-SNAPSHOT.jar" ;;
      note) source="fishhub-note/fishhub-note-biz/target/fishhub-note-biz-0.0.1-SNAPSHOT.jar" ;;
      relation) source="fishhub-user-relation/fishhub-user-relation-biz/target/fishhub-user-relation-biz-0.0.1-SNAPSHOT.jar" ;;
      comment) source="fishhub-comment/fishhub-comment-biz/target/fishhub-comment-biz-0.0.1-SNAPSHOT.jar" ;;
      auth) source="fishhub-auth/target/fishhub-auth-0.0.1-SNAPSHOT.jar" ;;
      data-align) source="fishhub-data-align/target/fishhub-data-align-0.0.1-SNAPSHOT.jar" ;;
      gateway) source="fishhub-gateway/target/fishhub-gateway-0.0.1-SNAPSHOT.jar" ;;
    esac
    if [[ ! -f "$project_root/$source" ]]; then
      print -u2 "缺少 $service 构建产物: $project_root/$source"
      exit 78
    fi
    cp "$project_root/$source" "$release_root/jars/$service.jar"
  done
  ln -s "$release_root" "$runtime_root/current.$release_id"
  # BSD mv follows an existing symlink to a directory, which would place the
  # new link inside the previous release instead of switching releases.
  if [[ -e "$runtime_root/current" && ! -L "$runtime_root/current" ]]; then
    print -u2 "运行目录 current 不是软链接，拒绝覆盖：$runtime_root/current"
    exit 78
  fi
  rm -f "$runtime_root/current"
  mv "$runtime_root/current.$release_id" "$runtime_root/current"
}

load_service() {
  local service=$1 label="com.fishhub.$1" plist="$launch_agents/com.fishhub.$1.plist"
  if launchctl print "gui/$uid/$label" >/dev/null 2>&1; then
    launchctl kickstart -k "gui/$uid/$label"
  else
    launchctl bootstrap "gui/$uid" "$plist"
  fi
}

stop_service() {
  local service=$1 label="com.fishhub.$1"
  if launchctl print "gui/$uid/$label" >/dev/null 2>&1; then
    launchctl bootout "gui/$uid/$label"
  fi
}

stop_all() {
  local service
  for service in "${reverse_services[@]}"; do
    stop_service "$service" || true
  done
}

wait_port() {
  local port=$1 service=$2 tries=0
  until nc -z 127.0.0.1 "$port" >/dev/null 2>&1; do
    (( tries += 1 ))
    if (( tries >= 120 )); then
      print -u2 "$service 未在 120 秒内监听 $port；请查看 $runtime_root/logs/$service.err"
      return 1
    fi
    sleep 1
  done
}

case "${1:-}" in
  install) install ;;
  start)
    install
    for service in "${base_services[@]}"; do load_service "$service"; done
    wait_port 7777 xxl-job-admin
    wait_port 8085 id
    wait_port 8084 kv
    wait_port 8081 oss
    wait_port 8090 count
    wait_port 8092 search
    for service in "${business_services[@]}"; do load_service "$service"; done
    wait_port 8082 user
    wait_port 8086 note
    wait_port 8087 relation
    wait_port 8093 comment
    wait_port 8080 auth
    wait_port 8091 data-align
    load_service gateway
    wait_port 8000 gateway
    print "FishHub 服务已由 launchd 托管并启动完成。"
    ;;
  stop)
    stop_all
    ;;
  restart)
    "$0" start
    ;;
  status)
    for service in "${services[@]}"; do
      if launchctl print "gui/$uid/com.fishhub.$service" >/dev/null 2>&1; then
        print "$service: managed"
      else
        print "$service: stopped"
      fi
    done
    ;;
  *) usage; exit 64 ;;
esac
