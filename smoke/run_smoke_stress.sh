#!/usr/bin/env bash
# =====================================================================
#           FishHub 全链路高并发压力测试一键启动器 (Linux/macOS)
# =====================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# 1. 检查 Python 环境
if command -v python3 &>/dev/null; then
    PYTHON_CMD=python3
elif command -v python &>/dev/null; then
    PYTHON_CMD=python
else
    echo -e "\033[91m[错误] 未检测到 Python 环境，请先安装 Python 3.8+！\033[0m"
    exit 1
fi

# 2. 拉起压测引擎
$PYTHON_CMD run_all.py "$@"
