# -*- coding: utf-8 -*-
"""
FishHub 高并发 HTTP 客户端
内置连接池复用、高精度耗时统计、Sa-Token 自动提取与注入、自动降级容错。
"""
import time
import json
import threading
from urllib.parse import urljoin

try:
    import requests
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False
    import urllib.request
    import urllib.error

from .config import GATEWAY_URL, SERVICES_DIRECT, REQUEST_TIMEOUT


class FishHubHttpClient:
    """线程安全的压测 HTTP 客户端"""

    def __init__(self, use_gateway: bool = True, default_timeout: float = REQUEST_TIMEOUT):
        self.use_gateway = use_gateway
        self.default_timeout = default_timeout
        self._local = threading.local()
        self.token_cache = {}  # userId -> Sa-Token
        self.token_lock = threading.Lock()

    def _get_session(self):
        """为每个工作线程维护一个带高容量连接池的 Session"""
        if not HAS_REQUESTS:
            return None
        if not hasattr(self._local, "session"):
            s = requests.Session()
            adapter = HTTPAdapter(
                pool_connections=100,
                pool_maxsize=200,
                max_retries=Retry(total=1, backoff_factor=0.1)
            )
            s.mount("http://", adapter)
            s.mount("https://", adapter)
            self._local.session = s
        return self._local.session

    def set_token(self, user_id: int, token: str):
        with self.token_lock:
            self.token_cache[user_id] = token

    def get_token(self, user_id: int) -> str:
        with self.token_lock:
            return self.token_cache.get(user_id, "")

    def request(
        self,
        method: str,
        service_or_path: str,
        path: str = None,
        json_data: dict = None,
        params: dict = None,
        user_id: int = None,
        token: str = None,
        headers: dict = None,
        timeout: float = None,
    ) -> tuple:
        """
        统一请求发起入口
        :return: (latency_ms, http_status, is_success, biz_code, response_data, error_msg)
        """
        req_headers = {"Content-Type": "application/json"}
        if headers:
            req_headers.update(headers)

        if token:
            req_headers["Authorization"] = f"Bearer {token}"
        elif user_id:
            cached_token = self.get_token(user_id)
            if cached_token:
                req_headers["Authorization"] = f"Bearer {cached_token}"
            else:
                # 直连或网关免鉴权链路使用 userId 请求头
                req_headers["userId"] = str(user_id)

        # 构建 URL
        if path is None:
            # 形式 1: 直接传完整路由如 "/note/note/detail" 或 "/auth/captcha"
            full_path = service_or_path if service_or_path.startswith("/") else f"/{service_or_path}"
            if self.use_gateway:
                url = urljoin(GATEWAY_URL, full_path)
            else:
                # 直连降级模式：根据前缀自动识别微服务目标端口
                prefix = full_path.split("/")[1] if len(full_path.split("/")) > 1 else ""
                if prefix in SERVICES_DIRECT:
                    base = SERVICES_DIRECT[prefix]
                    sub_path = full_path[len(prefix) + 1 :]
                    url = urljoin(base, sub_path if sub_path.startswith("/") else f"/{sub_path}")
                elif prefix == "auth":
                    base = SERVICES_DIRECT.get("user", GATEWAY_URL)
                    url = urljoin(base, full_path)
                else:
                    url = urljoin(GATEWAY_URL, full_path)
        else:
            # 形式 2: 传 (service_name, path) 如 ("note", "/note/detail")
            service_name = service_or_path
            sub_path = path if path.startswith("/") else f"/{path}"
            if self.use_gateway:
                route_prefix = f"/{service_name}"
                url = urljoin(GATEWAY_URL, f"{route_prefix}{sub_path}")
            else:
                base = SERVICES_DIRECT.get(service_name, GATEWAY_URL)
                url = urljoin(base, sub_path)

        req_timeout = timeout or self.default_timeout
        start_t = time.perf_counter()

        # 1. 使用 requests 发送请求
        if HAS_REQUESTS:
            session = self._get_session()
            try:
                resp = session.request(
                    method=method.upper(),
                    url=url,
                    json=json_data,
                    params=params,
                    headers=req_headers,
                    timeout=req_timeout,
                )
                latency_ms = (time.perf_counter() - start_t) * 1000.0
                http_status = resp.status_code
                try:
                    resp_json = resp.json()
                    is_biz_ok = isinstance(resp_json, dict) and resp_json.get("success") is True
                    biz_code = resp_json.get("errorCode", "") if isinstance(resp_json, dict) else ""
                    return (latency_ms, http_status, (http_status == 200 and is_biz_ok), biz_code, resp_json, "")
                except Exception:
                    is_ok = (http_status == 200)
                    return (latency_ms, http_status, is_ok, "", resp.text, "" if is_ok else resp.text[:200])
            except Exception as e:
                latency_ms = (time.perf_counter() - start_t) * 1000.0
                return (latency_ms, 599, False, "CLIENT_EXCEPTION", None, str(e))

        # 2. 纯 Python 标准库 urllib 兜底（兼容未安装 requests 的极端环境）
        else:
            try:
                data_bytes = json.dumps(json_data).encode("utf-8") if json_data is not None else None
                req = urllib.request.Request(url, data=data_bytes, headers=req_headers, method=method.upper())
                with urllib.request.urlopen(req, timeout=req_timeout) as r:
                    latency_ms = (time.perf_counter() - start_t) * 1000.0
                    http_status = r.getcode()
                    body_text = r.read().decode("utf-8", errors="replace")
                    try:
                        resp_json = json.loads(body_text)
                        is_biz_ok = isinstance(resp_json, dict) and resp_json.get("success") is True
                        biz_code = resp_json.get("errorCode", "") if isinstance(resp_json, dict) else ""
                        return (latency_ms, http_status, (http_status == 200 and is_biz_ok), biz_code, resp_json, "")
                    except Exception:
                        return (latency_ms, http_status, http_status == 200, "", body_text, "")
            except urllib.error.HTTPError as e:
                latency_ms = (time.perf_counter() - start_t) * 1000.0
                err_body = e.read().decode("utf-8", errors="replace") if e.fp else str(e)
                return (latency_ms, e.code, False, "HTTP_ERROR", None, err_body[:200])
            except Exception as e:
                latency_ms = (time.perf_counter() - start_t) * 1000.0
                return (latency_ms, 599, False, "CLIENT_EXCEPTION", None, str(e))
