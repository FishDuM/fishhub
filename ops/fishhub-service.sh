#!/bin/zsh
set -euo pipefail

if [[ $# -ne 1 ]]; then
  print -u2 "用法: $0 <service>"
  exit 64
fi

project_root=${0:A:h:h}
service=$1

case "$service" in
  xxl-job-admin) jar="xxl-job/xxl-job-admin/target/xxl-job-admin-2.4.1.jar"; memory=("-Xms128m" "-Xmx256m") ;;
  id) jar="fishhub-distributed-id-generator/fishhub-distributed-id-generator-biz/target/fishhub-distributed-id-generator-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms64m" "-Xmx192m") ;;
  kv) jar="fishhub-kv/fishhub-kv-biz/target/fishhub-kv-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms64m" "-Xmx192m") ;;
  oss) jar="fishhub-oss/fishhub-oss-biz/target/fishhub-oss-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms64m" "-Xmx192m") ;;
  count) jar="fishhub-count/fishhub-count-biz/target/fishhub-count-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms64m" "-Xmx256m") ;;
  search) jar="fishhub-search/fishhub-search-biz/target/fishhub-search-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx256m") ;;
  user) jar="fishhub-user/fishhub-user-biz/target/fishhub-user-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx384m") ;;
  note) jar="fishhub-note/fishhub-note-biz/target/fishhub-note-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx384m") ;;
  relation) jar="fishhub-user-relation/fishhub-user-relation-biz/target/fishhub-user-relation-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx384m") ;;
  comment) jar="fishhub-comment/fishhub-comment-biz/target/fishhub-comment-biz-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx384m") ;;
  auth) jar="fishhub-auth/target/fishhub-auth-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx256m") ;;
  data-align) jar="fishhub-data-align/target/fishhub-data-align-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx384m") ;;
  gateway) jar="fishhub-gateway/target/fishhub-gateway-0.0.1-SNAPSHOT.jar"; memory=("-Xms128m" "-Xmx384m") ;;
  *) print -u2 "未知服务: $service"; exit 64 ;;
esac

if [[ -n "${FISHHUB_RUNTIME_ROOT:-}" ]]; then
  project_root=$FISHHUB_RUNTIME_ROOT
  jar="jars/$service.jar"
fi

cd "$project_root"
if [[ ! -f "$jar" ]]; then
  print -u2 "找不到构建产物: $project_root/$jar；请先执行 zsh ops/fishhubctl.sh install"
  exit 78
fi

java_home=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
if [[ -z "$java_home" ]]; then
  print -u2 "未找到 JDK 17"
  exit 69
fi

exec "$java_home/bin/java" "${memory[@]}" -jar "$jar"
