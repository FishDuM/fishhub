# -*- coding: utf-8 -*-
"""
FishHub 性能统计与图表生成器
精确计算并发压测场景下的 QPS/TPS、P50/P90/P95/P99 延迟分位数、成功率及错误分类，
支持输出终端彩色报表、Markdown 报表和 HTML 报表。
"""
import time
import os
import json
import statistics
from datetime import datetime


class ScenarioStats:
    """单场景性能指标统计器"""

    def __init__(self, name: str, category: str, description: str = ""):
        self.name = name
        self.category = category
        self.description = description
        self.latencies = []          # 单位：毫秒 (ms)
        self.status_codes = {}       # HTTP 状态码计数
        self.biz_codes = {}          # 业务返回码计数
        self.errors = []             # 错误信息记录
        self.total_requests = 0
        self.success_requests = 0
        self.start_time = None
        self.end_time = None

    def start(self):
        self.start_time = time.perf_counter()

    def finish(self):
        self.end_time = time.perf_counter()

    def record(self, latency_ms: float, http_status: int, is_success: bool, biz_code: str = "", error_msg: str = ""):
        """记录单次请求结果"""
        self.total_requests += 1
        if is_success:
            self.success_requests += 1
        self.latencies.append(latency_ms)
        self.status_codes[http_status] = self.status_codes.get(http_status, 0) + 1
        if biz_code:
            self.biz_codes[biz_code] = self.biz_codes.get(biz_code, 0) + 1
        if error_msg and not is_success:
            if len(self.errors) < 20:  # 限制错误采样数量，避免内存膨胀
                self.errors.append(f"[{http_status}] {error_msg}")

    @property
    def duration(self) -> float:
        if self.start_time and self.end_time:
            return max(self.end_time - self.start_time, 0.001)
        return 0.001

    @property
    def qps(self) -> float:
        return self.total_requests / self.duration

    @property
    def success_rate(self) -> float:
        if self.total_requests == 0:
            return 0.0
        return (self.success_requests / self.total_requests) * 100.0

    def percentile(self, p: float) -> float:
        """计算百分位延迟 (ms)"""
        if not self.latencies:
            return 0.0
        sorted_l = sorted(self.latencies)
        k = (len(sorted_l) - 1) * (p / 100.0)
        f = int(k)
        c = min(f + 1, len(sorted_l) - 1)
        d = k - f
        return sorted_l[f] + (sorted_l[c] - sorted_l[f]) * d

    def summary_dict(self) -> dict:
        sorted_l = sorted(self.latencies) if self.latencies else [0.0]
        return {
            "name": self.name,
            "category": self.category,
            "description": self.description,
            "total": self.total_requests,
            "success": self.success_requests,
            "failed": self.total_requests - self.success_requests,
            "success_rate": round(self.success_rate, 2),
            "duration_s": round(self.duration, 3),
            "qps": round(self.qps, 1),
            "min_ms": round(min(sorted_l), 2),
            "avg_ms": round(statistics.mean(sorted_l), 2) if sorted_l else 0.0,
            "p50_ms": round(self.percentile(50), 2),
            "p90_ms": round(self.percentile(90), 2),
            "p95_ms": round(self.percentile(95), 2),
            "p99_ms": round(self.percentile(99), 2),
            "max_ms": round(max(sorted_l), 2),
            "status_codes": self.status_codes,
            "sample_errors": self.errors[:5],
        }


def get_display_width(s: str) -> int:
    """计算包含中英文字符串的控制台显示宽度"""
    width = 0
    for ch in s:
        width += 2 if ord(ch) > 127 else 1
    return width


def pad_display(s: str, target_width: int, align: str = "left") -> str:
    """对齐包含中文的控制台字符串"""
    current_width = get_display_width(s)
    pad_len = max(target_width - current_width, 0)
    if align == "right":
        return " " * pad_len + s
    elif align == "center":
        left_pad = pad_len // 2
        right_pad = pad_len - left_pad
        return " " * left_pad + s + " " * right_pad
    return s + " " * pad_len


class ReportPrinter:
    """格式化报告展示与导出"""

    # ANSI 颜色定义
    GREEN = "\033[92m"
    RED = "\033[91m"
    YELLOW = "\033[93m"
    CYAN = "\033[96m"
    BOLD = "\033[1m"
    RESET = "\033[0m"

    @classmethod
    def print_terminal_summary(cls, stats_list: list):
        """在控制台打印高可读性彩色汇总表格"""
        name_w = 24
        req_w = 8
        rate_w = 9
        qps_w = 10
        avg_w = 9
        p50_w = 9
        p95_w = 9
        p99_w = 9
        status_w = 6

        print("\n" + "=" * 115)
        print(f"{cls.BOLD}{cls.CYAN}                      FishHub 全链路高并发压力测试 - 最终成绩总榜{cls.RESET}")
        print("=" * 115)

        header = (
            f"| {pad_display('场景名称', name_w)} "
            f"| {pad_display('总请求', req_w)} "
            f"| {pad_display('成功率', rate_w)} "
            f"| {pad_display('QPS/TPS', qps_w)} "
            f"| {pad_display('Avg(ms)', avg_w)} "
            f"| {pad_display('P50(ms)', p50_w)} "
            f"| {pad_display('P95(ms)', p95_w)} "
            f"| {pad_display('P99(ms)', p99_w)} "
            f"| {pad_display('状态', status_w)} |"
        )
        print(header)
        sep = (
            f"|{'-' * (name_w + 2)}"
            f"|{'-' * (req_w + 2)}"
            f"|{'-' * (rate_w + 2)}"
            f"|{'-' * (qps_w + 2)}"
            f"|{'-' * (avg_w + 2)}"
            f"|{'-' * (p50_w + 2)}"
            f"|{'-' * (p95_w + 2)}"
            f"|{'-' * (p99_w + 2)}"
            f"|{'-' * (status_w + 2)}|"
        )
        print(sep)

        total_reqs = 0
        total_succ = 0
        all_durations = []

        for s in stats_list:
            d = s.summary_dict()
            total_reqs += d["total"]
            total_succ += d["success"]
            all_durations.append(d["duration_s"])

            status_str = f"{cls.GREEN}PASS{cls.RESET}" if d["success_rate"] >= 95.0 else f"{cls.RED}FAIL{cls.RESET}"
            rate_color = cls.GREEN if d["success_rate"] >= 99.0 else (cls.YELLOW if d["success_rate"] >= 90.0 else cls.RED)
            rate_text = f"{d['success_rate']:>6.1f}%"
            rate_formatted = f"{rate_color}{rate_text}{cls.RESET}"

            line = (
                f"| {pad_display(d['name'], name_w)} "
                f"| {pad_display(str(d['total']), req_w)} "
                f"| {rate_formatted}  "
                f"| {pad_display(str(d['qps']), qps_w)} "
                f"| {pad_display(str(d['avg_ms']), avg_w)} "
                f"| {pad_display(str(d['p50_ms']), p50_w)} "
                f"| {pad_display(str(d['p95_ms']), p95_w)} "
                f"| {pad_display(str(d['p99_ms']), p99_w)} "
                f"| {status_str}   |"
            )
            print(line)

        print("=" * 115)
        overall_rate = (total_succ / total_reqs * 100.0) if total_reqs > 0 else 0.0
        print(f"{cls.BOLD}总计发送请求: {total_reqs} 次 | 成功: {total_succ} 次 | 全局通过率: {overall_rate:.2f}%{cls.RESET}")
        print("=" * 115 + "\n")

    @classmethod
    def save_reports(cls, stats_list: list, output_dir: str) -> str:
        """保存 Markdown 和 HTML 报告到本地文件"""
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)

        now_str = datetime.now().strftime("%Y%m%d_%H%M%S")
        md_file = os.path.join(output_dir, f"smoke_report_{now_str}.md")
        html_file = os.path.join(output_dir, f"smoke_report_{now_str}.html")

        # 生成 Markdown 内容
        md_lines = [
            f"# FishHub 压测性能分析报告 ({now_str})",
            "",
            f"- **测试执行时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            f"- **参与压测场景数**: {len(stats_list)}",
            "",
            "## 核心性能指标总览",
            "",
            "| 场景名称 | 所属链路 | 总请求数 | 成功请求 | 成功率 | QPS | Avg (ms) | P50 (ms) | P95 (ms) | P99 (ms) | 评级 |",
            "| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |",
        ]

        for s in stats_list:
            d = s.summary_dict()
            badge = "🟢 PASS" if d["success_rate"] >= 95.0 else "🔴 FAIL"
            md_lines.append(
                f"| `{d['name']}` | {d['category']} | {d['total']} | {d['success']} | **{d['success_rate']}%** | **{d['qps']}** | {d['avg_ms']} | {d['p50_ms']} | {d['p95_ms']} | {d['p99_ms']} | {badge} |"
            )

        md_lines.append("\n## 错误与异常明细\n")
        has_error = False
        for s in stats_list:
            d = s.summary_dict()
            if d["failed"] > 0 and d["sample_errors"]:
                has_error = True
                md_lines.append(f"### 场景: {d['name']}")
                md_lines.append(f"- 失败数: {d['failed']}")
                md_lines.append("- 错误样例:")
                for err in d["sample_errors"]:
                    md_lines.append(f"  - `{err}`")
                md_lines.append("")

        if not has_error:
            md_lines.append("🎉 **全场景无任何异常，100% 成功通过！**")

        with open(md_file, "w", encoding="utf-8") as f:
            f.write("\n".join(md_lines))

        return md_file
