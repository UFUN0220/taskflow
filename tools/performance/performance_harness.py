#!/usr/bin/env python3
"""Prepare repeatable TaskFlow data and run HTTP performance scenarios.

The script intentionally uses only Python's standard library so it can run on
Windows 11 without adding a benchmark dependency to the application.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import queue
import random
import statistics
import string
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable


SUCCESS_CODE = "0"
SCENARIOS = ("login", "task_list", "task_detail", "task_create", "state_update", "notification_list")


class ApiError(RuntimeError):
    def __init__(self, status: int, code: str | None, message: str, body: Any = None):
        super().__init__(f"HTTP {status}, code={code or 'n/a'}, message={message}")
        self.status = status
        self.code = code
        self.body = body


@dataclass
class HttpResponse:
    status: int
    body: Any
    elapsed_ms: float


class ApiClient:
    def __init__(self, base_url: str, token: str | None = None, timeout: float = 15.0):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout = timeout

    def request(self, method: str, path: str, payload: Any = None, query: dict[str, Any] | None = None) -> HttpResponse:
        url = self.base_url + path
        if query:
            encoded = [(key, value) for key, value in query.items() if value is not None]
            url += "?" + urllib.parse.urlencode(encoded)
        data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                status = response.status
        except urllib.error.HTTPError as error:
            raw = error.read()
            status = error.code
        except urllib.error.URLError as error:
            elapsed_ms = (time.perf_counter() - started) * 1000
            raise ApiError(0, None, str(error.reason)) from error
        elapsed_ms = (time.perf_counter() - started) * 1000
        try:
            body = json.loads(raw.decode("utf-8")) if raw else None
        except json.JSONDecodeError:
            body = raw.decode("utf-8", errors="replace")
        return HttpResponse(status, body, elapsed_ms)

    def require_success(self, response: HttpResponse) -> Any:
        body = response.body if isinstance(response.body, dict) else {}
        code = body.get("code")
        if response.status < 200 or response.status >= 300 or code != SUCCESS_CODE:
            raise ApiError(response.status, code, str(body.get("message", "request failed")), response.body)
        return body.get("data")

    def login(self, login: str, password: str) -> str:
        response = self.request("POST", "/api/auth/login", {"login": login, "password": password})
        data = self.require_success(response)
        token = data.get("accessToken") if isinstance(data, dict) else None
        if not token:
            raise RuntimeError("登录响应未返回 accessToken")
        return token


def flatten_departments(nodes: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for node in nodes:
        result.append(node)
        result.extend(flatten_departments(node.get("children") or []))
    return result


def fetch_page(client: ApiClient, path: str, params: dict[str, Any]) -> list[dict[str, Any]]:
    data = client.require_success(client.request("GET", path, query=params))
    if not isinstance(data, dict):
        raise RuntimeError(f"{path} 未返回分页对象")
    return data.get("records") or []


def fetch_all_pages(client: ApiClient, path: str, size: int = 100) -> list[dict[str, Any]]:
    page = 1
    records: list[dict[str, Any]] = []
    while True:
        data = client.require_success(client.request("GET", path, query={"page": page, "size": size}))
        if not isinstance(data, dict):
            raise RuntimeError(f"{path} 未返回分页对象")
        current = data.get("records") or []
        records.extend(current)
        total = int(data.get("total") or len(records))
        if not current or len(records) >= total or len(current) < size:
            return records
        page += 1


def ensure_departments(client: ApiClient, count: int) -> list[int]:
    tree = client.require_success(client.request("GET", "/api/departments/tree")) or []
    existing = flatten_departments(tree)
    by_code = {str(item.get("departmentCode")): item for item in existing}
    perf_ids = [int(item["id"]) for item in existing if str(item.get("departmentCode", "")).startswith("PERF_DEPT_")]
    for index in range(1, count + 1):
        code = f"PERF_DEPT_{index:05d}"
        if code not in by_code:
            data = client.require_success(client.request("POST", "/api/departments", {
                "departmentCode": code,
                "departmentName": f"Performance Department {index:05d}",
                "parentId": None,
            }))
            perf_ids.append(int(data["departmentId"]))
    return perf_ids[:count]


def ensure_users(client: ApiClient, count: int, department_ids: list[int], password: str) -> list[dict[str, Any]]:
    existing = fetch_all_pages(client, "/api/users")
    by_username = {
        str(item.get("username")): item
        for item in existing
        if str(item.get("username", "")).startswith("perf_user_")
    }
    for index in range(1, count + 1):
        username = f"perf_user_{index:05d}"
        if username not in by_username:
            data = client.require_success(client.request("POST", "/api/users", {
                "username": username,
                "employeeNo": f"PERF{index:08d}",
                "displayName": f"Performance User {index:05d}",
                "password": password,
                "departmentId": department_ids[(index - 1) % len(department_ids)] if department_ids else None,
                "roleCodes": ["employee"],
            }))
            by_username[username] = data
    return [by_username[f"perf_user_{index:05d}"] for index in range(1, count + 1)]


def ensure_tasks(client: ApiClient, count: int, department_ids: list[int], user_ids: list[int]) -> list[dict[str, Any]]:
    existing = fetch_all_pages(client, "/api/tasks")
    by_task_no = {
        str(item.get("taskNo")): item
        for item in existing
        if str(item.get("taskNo", "")).startswith("PERF_TASK_")
    }
    priorities = ("LOW", "MEDIUM", "HIGH", "URGENT")
    for index in range(1, count + 1):
        task_no = f"PERF_TASK_{index:08d}"
        if task_no not in by_task_no:
            data = client.require_success(client.request("POST", "/api/tasks", {
                "taskNo": task_no,
                "title": f"Performance Task {index:08d}",
                "description": "Generated by tools/performance/performance_harness.py",
                "departmentId": department_ids[(index - 1) % len(department_ids)] if department_ids else None,
                "priority": priorities[(index - 1) % len(priorities)],
                "primaryAssigneeId": user_ids[(index - 1) % len(user_ids)],
                "collaboratorIds": [],
            }))
            by_task_no[task_no] = data
    return [by_task_no[f"PERF_TASK_{index:08d}"] for index in range(1, count + 1)]


def prepare(client: ApiClient, args: argparse.Namespace) -> dict[str, Any]:
    token = client.login(args.admin_login, args.admin_password)
    client.token = token
    department_ids = ensure_departments(client, args.departments)
    users = ensure_users(client, args.users, department_ids, args.user_password)
    user_ids = [int(item["userId"]) for item in users]
    tasks = ensure_tasks(client, args.tasks, department_ids, user_ids)
    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "baseUrl": client.base_url,
        "departments": len(department_ids),
        "users": len(user_ids),
        "tasks": len(tasks),
        "taskIds": [item.get("taskId") for item in tasks],
    }
    return summary


def discover_task_snapshots(client: ApiClient) -> list[dict[str, Any]]:
    return [item for item in fetch_all_pages(client, "/api/tasks")
            if str(item.get("taskNo", "")).startswith("PERF_TASK_")]


@dataclass
class ScenarioStats:
    latencies: list[float] = field(default_factory=list)
    successes: int = 0
    errors: int = 0
    status_counts: dict[str, int] = field(default_factory=dict)

    def add(self, elapsed_ms: float, success: bool, status: int) -> None:
        self.latencies.append(elapsed_ms)
        if success:
            self.successes += 1
        else:
            self.errors += 1
        key = str(status)
        self.status_counts[key] = self.status_counts.get(key, 0) + 1

    def report(self) -> dict[str, Any]:
        values = sorted(self.latencies)
        total = len(values)

        def percentile(percent: float) -> float | None:
            if not values:
                return None
            index = min(total - 1, max(0, int(round((percent / 100) * (total - 1)))))
            return round(values[index], 3)

        return {
            "requests": total,
            "successes": self.successes,
            "errors": self.errors,
            "errorRate": round(self.errors / total, 6) if total else None,
            "qps": round(total / max(0.001, self._duration_seconds), 3) if total else 0,
            "averageMs": round(statistics.fmean(values), 3) if values else None,
            "p95Ms": percentile(95),
            "p99Ms": percentile(99),
            "statusCodes": self.status_counts,
        }

    _duration_seconds: float = field(default=1.0, repr=False)


def run_load(client: ApiClient, args: argparse.Namespace, tasks: list[dict[str, Any]]) -> dict[str, Any]:
    selected = [value.strip() for value in args.scenarios.split(",") if value.strip()]
    unknown = sorted(set(selected) - set(SCENARIOS))
    if not selected or unknown:
        raise ValueError(f"未知场景: {unknown or 'empty'}，可选值为 {', '.join(SCENARIOS)}")
    if "task_detail" in selected or "state_update" in selected or "task_create" in selected:
        if not tasks:
            raise RuntimeError("没有可用的 PERF_TASK_* 数据，请先执行 prepare")

    task_ids = [int(item["taskId"]) for item in tasks]
    state_queue: queue.Queue[tuple[int, int]] = queue.Queue()
    for item in tasks:
        state_queue.put((int(item["taskId"]), int(item.get("version") or 0)))
    user_ids = [int(item.get("creatorId")) for item in tasks if item.get("creatorId")]
    user_ids = user_ids or [1]
    random_seed = args.seed
    deadline = time.monotonic() + args.warmup_seconds + args.duration_seconds
    record_after = time.monotonic() + args.warmup_seconds
    stats = {name: ScenarioStats(_duration_seconds=args.duration_seconds) for name in selected}
    lock = threading.Lock()

    def make_request(worker_client: ApiClient, scenario: str) -> HttpResponse:
        if scenario == "login":
            return worker_client.request("POST", "/api/auth/login", {
                "login": args.admin_login,
                "password": args.admin_password,
            })
        if scenario == "task_list":
            return worker_client.request("GET", "/api/tasks", query={"page": 1, "size": 20, "status": "DRAFT"})
        if scenario == "task_detail":
            return worker_client.request("GET", f"/api/tasks/{random.choice(task_ids)}")
        if scenario == "task_create":
            suffix = "".join(random.choice(string.ascii_uppercase + string.digits) for _ in range(12))
            return worker_client.request("POST", "/api/tasks", {
                "taskNo": f"PERF_RUN_{suffix}",
                "title": "Performance run task",
                "description": "Generated during the configured performance run",
                "priority": "MEDIUM",
                "primaryAssigneeId": random.choice(user_ids),
                "collaboratorIds": [],
            })
        if scenario == "state_update":
            task_id, version = state_queue.get_nowait()
            return worker_client.request("POST", f"/api/tasks/{task_id}/submit", {"version": version})
        if scenario == "notification_list":
            return worker_client.request("GET", "/api/notifications", query={"page": 1, "size": 20, "status": "UNREAD"})
        raise AssertionError(scenario)

    def worker(worker_index: int) -> None:
        rng = random.Random(random_seed + worker_index)
        worker_client = ApiClient(client.base_url, client.token, client.timeout)
        while time.monotonic() < deadline:
            available = [name for name in selected
                         if name != "state_update" or not state_queue.empty()]
            if not available:
                return
            scenario = rng.choice(available)
            started = time.perf_counter()
            status = 0
            success = False
            try:
                response = make_request(worker_client, scenario)
                status = response.status
                body = response.body if isinstance(response.body, dict) else {}
                success = 200 <= response.status < 300 and body.get("code") == SUCCESS_CODE
            except queue.Empty:
                continue
            except (ApiError, OSError, ValueError):
                status = 0
            if time.monotonic() >= record_after:
                elapsed_ms = (time.perf_counter() - started) * 1000
                with lock:
                    stats[scenario].add(elapsed_ms, success, status)

    workers = [threading.Thread(target=worker, args=(index,), daemon=True)
               for index in range(args.concurrency)]
    for worker_thread in workers:
        worker_thread.start()
    for worker_thread in workers:
        worker_thread.join()
    result = {name: item.report() for name, item in stats.items()}
    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "baseUrl": client.base_url,
        "parameters": {
            "scenarios": selected,
            "concurrency": args.concurrency,
            "warmupSeconds": args.warmup_seconds,
            "durationSeconds": args.duration_seconds,
            "seed": args.seed,
            "preparedTasks": len(tasks),
        },
        "machine": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "cpuCount": os.cpu_count(),
            "jvmArgs": os.environ.get("JVM_ARGS", "not-provided"),
        },
        "scenarios": result,
    }


def write_or_print(value: dict[str, Any], output: str | None) -> None:
    rendered = json.dumps(value, ensure_ascii=False, indent=2)
    if output:
        target = Path(output)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(rendered + "\n", encoding="utf-8")
        print(f"结果已写入 {target}")
    else:
        print(rendered)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="TaskFlow 阶段15数据准备和HTTP压测工具")
    parser.add_argument("command", choices=("prepare", "run"))
    parser.add_argument("--base-url", default=os.environ.get("TASKFLOW_PERF_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--admin-login", default=os.environ.get("TASKFLOW_ACCEPTANCE_ADMIN_USERNAME", ""))
    parser.add_argument("--admin-password", default=os.environ.get("TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD", ""))
    parser.add_argument("--user-password", default=os.environ.get("TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD", ""))
    parser.add_argument("--departments", type=int, default=10)
    parser.add_argument("--users", type=int, default=100)
    parser.add_argument("--tasks", type=int, default=1000)
    parser.add_argument("--timeout-seconds", type=float, default=15)
    parser.add_argument("--output")
    parser.add_argument("--concurrency", type=int, default=10)
    parser.add_argument("--warmup-seconds", type=int, default=10)
    parser.add_argument("--duration-seconds", type=int, default=60)
    parser.add_argument("--scenarios", default=",".join(SCENARIOS))
    parser.add_argument("--seed", type=int, default=20260807)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if min(args.departments, args.users, args.tasks, args.concurrency) < 1:
        raise SystemExit("departments/users/tasks/concurrency 必须大于 0")
    if not args.admin_login:
        raise SystemExit("请通过 --admin-login 或 TASKFLOW_ACCEPTANCE_ADMIN_USERNAME 提供管理员用户名")
    if not args.admin_password:
        raise SystemExit("请通过 --admin-password 或 TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD 提供管理员密码")
    if not args.user_password:
        raise SystemExit("请通过 --user-password 或 TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD 提供性能测试用户密码")
    client = ApiClient(args.base_url, timeout=args.timeout_seconds)
    try:
        if args.command == "prepare":
            write_or_print(prepare(client, args), args.output)
            return 0
        token = client.login(args.admin_login, args.admin_password)
        client.token = token
        tasks = discover_task_snapshots(client)
        write_or_print(run_load(client, args, tasks), args.output)
        return 0
    except (ApiError, RuntimeError, ValueError) as error:
        print(f"性能工具执行失败：{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
