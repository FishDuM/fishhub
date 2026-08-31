# -*- coding: utf-8 -*-
"""
FishHub 压力测试套件总入口 (Runner)
支持全链路一键自动压测或单场景选择压测，全彩进度显示，自动生成分析报表。
"""
import sys
import os
import argparse
import time

# 将项目根目录与 smoke 目录加入 sys.path
SMOKE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SMOKE_DIR, ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)
if SMOKE_DIR not in sys.path:
    sys.path.insert(0, SMOKE_DIR)

# 确保在 Windows 控制台下不发生 Unicode 编码崩溃
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

from smoke.config import (
    GATEWAY_URL,
    SERVICES_DIRECT,
    DEFAULT_CONCURRENCY,
    DEFAULT_ROUNDS,
    OUTPUT_REPORT_DIR,
    detect_environment,
)
from smoke.http_client import FishHubHttpClient, HAS_REQUESTS
from smoke.stats import ReportPrinter

# 导入所有场景模块
from smoke.scenarios import (
    test_01_auth_user,
    test_02_relation,
    test_03_note_interact,
    test_04_comment,
    test_05_search,
    test_06_count,
    test_07_mixed_e2e,
)

SCENARIO_MODULES = [
    ("1", "用户认证与用户域 (Auth & User)", test_01_auth_user),
    ("2", "社交关注与关系域 (Relation & Follow)", test_02_relation),
    ("3", "笔记内容与互动域 (Note & Interact)", test_03_note_interact),
    ("4", "评论互动与热度域 (Comment & Heat)", test_04_comment),
    ("5", "全文检索与搜索域 (Search & Query)", test_05_search),
    ("6", "高频聚合与计数域 (Count & Stats)", test_06_count),
    ("7", "全链路高拟真混合大促 (Mixed Heavy E2E)", test_07_mixed_e2e),
]


def print_banner():
    banner = r"""
========================================================================================
  ______ _     _     _    _       _       _____                     _           
 |  ____(_)   | |   | |  | |     | |     / ____|                   | |          
 | |__   _ ___| |__ | |__| |_   _| |__  | (___  _ __ ___   ___  ___| | __       
 |  __| | / __| '_ \|  __  | | | | '_ \  \___ \| '_ ` _ \ / _ \/ _ \ |/ /       
 | |    | \__ \ | | | |  | | |_| | |_) | ____) | | | | | | (_) |  __/   <        
 |_|    |_|___/_| |_|_|  |_|\__,_|_.__/ |_____/|_| |_| |_|\___/ \___|_|\_\       
                     全 链 路 高 并 发 极 限 压 力 测 试 套 件                   
========================================================================================
"""
    print("\033[96m" + banner + "\033[0m")


def check_and_print_env():
    print(">>> 正在探测后端各服务运行状态...")
    status = detect_environment()
    all_ok = True
    for name, alive in status.items():
        state_str = "\033[92m[UP 正常运行]\033[0m" if alive else "\033[93m[DOWN 未连接]\033[0m"
        port_info = "8000 (网关)" if name == "gateway" else f"{SERVICES_DIRECT.get(name, '')}"
        print(f"  - 服务 [{name.upper():<7}] -> {port_info:<24} {state_str}")
        if not alive and name == "gateway":
            all_ok = False

    if not status["gateway"]:
        print("\n\033[93m[提示]\033[0m 网关端口 8000 未启动。测试将尝试走直接降级调用模式。\n")
    else:
        print(f"\n\033[92m[就绪]\033[0m 网关已连通: {GATEWAY_URL}，将通过网关进行标准生产链路压测！\n")
    return status


def main():
    print_banner()

    parser = argparse.ArgumentParser(description="FishHub 压力测试启动器")
    parser.add_argument("-c", "--concurrency", type=int, default=None, help=f"并发工作线程数 (默认: {DEFAULT_CONCURRENCY})")
    parser.add_argument("-r", "--rounds", type=int, default=None, help=f"单场景请求轮次 (默认: {DEFAULT_ROUNDS})")
    parser.add_argument("-s", "--scenario", type=str, default=None, help="指定运行场景序号 (1-7 或 all)")
    args = parser.parse_args()

    env_status = check_and_print_env()

    concurrency = args.concurrency
    rounds = args.rounds
    scenario = args.scenario

    # 若未指定 CLI 参数（如直接双击 bat 启动），弹出交互式菜单
    if scenario is None and concurrency is None and rounds is None:
        print("=" * 75)
        print("请选择压测模式 (直接回车默认 [1] 全链路标准压测):")
        print()
        print("  [1] 全链路标准压力测试 (全场景 1~7, 并发: 50, 轮次: 200) - [推荐]")
        print("  [2] 极速轻量压测       (全场景 1~7, 并发: 20, 轮次: 50)")
        print("  [3] 极限暴力冲击测试   (全场景 1~7, 并发: 100, 轮次: 500)")
        print("  [4] ⚙️  自定义并发与轮次 (自由输入并发线程数、请求轮次与测试场景)")
        print("  [5] 仅测试用户认证域   (场景 1: 验证码、登录、资料查询)")
        print("  [6] 仅测试社交关注域   (场景 2: 关注风暴、粉丝列表)")
        print("  [7] 仅测试笔记互动域   (场景 3: 笔记发布、热点读取、点赞收藏)")
        print("  [8] 仅测试评论热度域   (场景 4: 一级/二级评论、热度排序)")
        print("  [9] 仅测试混合大促链路 (场景 7: 60%读 + 20%互动 + 15%评 + 5%发)")
        print("  [10] 🧹 一键清理所有压测历史脏数据 (清理 DB 与 Redis)")
        print("=" * 75)
        try:
            choice = input("请输入选项编号 (1-10, 默认 1): ").strip()
        except (EOFError, KeyboardInterrupt):
            choice = "1"

        if choice == "10":
            from smoke.cleaner import clean_all
            clean_all(verbose=True)
            return

        if choice == "2":
            concurrency, rounds, scenario = 20, 50, "all"
        elif choice == "3":
            concurrency, rounds, scenario = 100, 500, "all"
        elif choice == "4":
            print("\n" + "-" * 55)
            print(">>> 进入自定义压测参数配置模式:")
            try:
                c_in = input("  [1/3] 请输入并发工作线程数 (默认 50, 推荐 10~500): ").strip()
                concurrency = int(c_in) if c_in.isdigit() and int(c_in) > 0 else 50
            except Exception:
                concurrency = 50

            try:
                r_in = input("  [2/3] 请输入单场景请求轮次 (默认 200, 推荐 20~2000): ").strip()
                rounds = int(r_in) if r_in.isdigit() and int(r_in) > 0 else 200
            except Exception:
                rounds = 200

            try:
                s_in = input("  [3/3] 请输入压测场景 (1-7，多选用逗号隔开，直接回车默认 all): ").strip()
                scenario = s_in if s_in else "all"
            except Exception:
                scenario = "all"
            print("-" * 55)
        elif choice == "5":
            concurrency, rounds, scenario = 50, 200, "1"
        elif choice == "6":
            concurrency, rounds, scenario = 50, 200, "2"
        elif choice == "7":
            concurrency, rounds, scenario = 50, 200, "3"
        elif choice == "8":
            concurrency, rounds, scenario = 50, 200, "4"
        elif choice == "9":
            concurrency, rounds, scenario = 50, 200, "7"
        else:
            concurrency, rounds, scenario = 50, 200, "all"
        print()
    else:
        concurrency = concurrency or DEFAULT_CONCURRENCY
        rounds = rounds or DEFAULT_ROUNDS
        scenario = scenario or "all"

    client = FishHubHttpClient(use_gateway=False)

    # 确定待执行的场景
    selected_scenarios = []
    if scenario.lower() == "all":
        selected_scenarios = SCENARIO_MODULES
    else:
        chosen_keys = [k.strip() for k in scenario.split(",")]
        for key, name, mod in SCENARIO_MODULES:
            if key in chosen_keys:
                selected_scenarios.append((key, name, mod))

    if not selected_scenarios:
        print(f"\033[91m[错误]\033[0m 未找到指定的场景序号: {scenario}，可用选项: 1~7 或 all")
        return

    print(f">>> 开始执行高并发压测 (并发线程: {concurrency}, 单场景轮次: {rounds})...\n")
    all_stats = []
    start_all_t = time.perf_counter()

    for idx, (key, name, module) in enumerate(selected_scenarios, 1):
        print(f"[{idx}/{len(selected_scenarios)}] 正在执行场景 {key}: \033[1m{name}\033[0m ...")
        t0 = time.perf_counter()
        try:
            scenario_stats = module.run(client, concurrency=concurrency, rounds=rounds)
            cost = time.perf_counter() - t0
            for s in scenario_stats:
                d = s.summary_dict()
                color = "\033[92m" if d["success_rate"] >= 95.0 else "\033[91m"
                print(f"    [+] {d['name']:<24} -> 请求数: {d['total']:<5} | 成功率: {color}{d['success_rate']}% \033[0m | QPS: {d['qps']:<6.1f} | P99: {d['p99_ms']}ms")
            all_stats.extend(scenario_stats)
        except Exception as e:
            print(f"    [-] 执行异常: {e}")

    total_cost = time.perf_counter() - start_all_t

    # 输出漂亮的表格成绩总榜
    ReportPrinter.print_terminal_summary(all_stats)

    # 导出报表到文件
    report_file = ReportPrinter.save_reports(all_stats, OUTPUT_REPORT_DIR)
    print(f"[报告] 压测报表已成功生成并保存至: \033[96m{report_file}\033[0m")
    print(f"[完成] 整个压测套件总耗时: {total_cost:.2f} 秒\n")

    # 压测后自动清理数据提示 (默认 Y 自动清理)
    try:
        clean_choice = input(">>> 是否立即自动清理本次压测产生的测试数据 (Y/n, 默认 Y): ").strip().lower()
    except (EOFError, KeyboardInterrupt):
        clean_choice = "y"

    if clean_choice != "n":
        from smoke.cleaner import clean_all
        clean_all(verbose=True)


if __name__ == "__main__":
    main()
