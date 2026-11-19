#!/usr/bin/env python3
"""Drive a headless `figcraft-cli mcp` over stdio to work in a live FigCraft session on a server.

Use it where the figcraft MCP tools aren't registered (e.g. a cloud session that can't restart
Claude Code). One run = one process: it starts `figcraft-cli mcp`, runs the steps in order, and
stops at the first failed step. The live session lasts only while this process runs, so put the
join, the edits and the save in the same steps file.

    python3 figcraft_live.py --cli /path/to/figcraft-cli steps.json [--out-dir DIR] [--show-secrets]

steps.json is a JSON array. Each step is one of:

    {"join": "<share link or join code>", "timeout": 180}
        collab.join, then poll collab.peers until "live" (fails with the server's reason)
    {"command": "node.set", "params": {...}}       run_command (any of FigCraft's commands)
    {"tool": "inspect_document", "args": {...}}    any MCP tool (save_file, screenshot, export…)
    {"sync": 5}                                    keep calling collab.peers for N seconds, so
                                                   edits go out and others' changes come in
    {"leave": true}                                collab.leave {} (the document stays open)

Each step prints one JSON line {"step", "kind", "ok", "result"}; images that tools return are
written to --out-dir (default: the current directory) and replaced by their path. Share links,
join codes and owner tokens in results are masked unless --show-secrets is given.
Exit code: 0 when every step passed, 1 when a step failed, 2 on a usage or startup error.
"""
import argparse
import base64
import json
import os
import queue
import re
import subprocess
import sys
import threading
import time

PROTOCOL = "2025-06-18"
SECRET_KEYS = {
    "link", "url", "edit", "view", "owner", "code", "editCode", "viewCode", "commentCode",
    "editLink", "viewLink", "commentLink", "token", "key",
}
SECRET_IN_TEXT = re.compile(r"((?:token|key)=)[^&\s\"']+")


def mask(value):
    """Hide share links, codes and tokens so they don't end up in logs or chat."""
    if isinstance(value, dict):
        return {k: ("***" if k in SECRET_KEYS and value[k] not in (None, "", False) else mask(v))
                for k, v in value.items()}
    if isinstance(value, list):
        return [mask(v) for v in value]
    if isinstance(value, str):
        return SECRET_IN_TEXT.sub(r"\1***", value)
    return value


class McpClient:
    def __init__(self, argv, stderr_path):
        self.stderr = open(stderr_path, "ab")
        self.proc = subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=self.stderr)
        self.lines = queue.Queue()
        threading.Thread(target=self._read, daemon=True).start()
        self.next_id = 0

    def _read(self):
        for raw in self.proc.stdout:
            self.lines.put(raw)
        self.lines.put(None)

    def request(self, method, params, timeout=120):
        self.next_id += 1
        rid = self.next_id
        msg = {"jsonrpc": "2.0", "id": rid, "method": method, "params": params}
        self.proc.stdin.write((json.dumps(msg, ensure_ascii=False) + "\n").encode("utf-8"))
        self.proc.stdin.flush()
        deadline = time.monotonic() + timeout
        while True:
            left = deadline - time.monotonic()
            if left <= 0:
                raise TimeoutError(f"no answer to {method} in {timeout} s")
            try:
                raw = self.lines.get(timeout=left)
            except queue.Empty:
                continue
            if raw is None:
                raise RuntimeError("figcraft-cli mcp exited (see its stderr log)")
            try:
                reply = json.loads(raw)
            except ValueError:
                continue  # not a JSON-RPC line
            if reply.get("id") != rid:
                continue  # a notification or a stray reply
            if "error" in reply:
                raise RuntimeError(f"{method}: {reply['error'].get('message', reply['error'])}")
            return reply.get("result")

    def notify(self, method, params=None):
        msg = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            msg["params"] = params
        self.proc.stdin.write((json.dumps(msg) + "\n").encode("utf-8"))
        self.proc.stdin.flush()

    def close(self):
        try:
            self.proc.stdin.close()
            self.proc.wait(timeout=10)
        except Exception:
            self.proc.kill()
        self.stderr.close()


class Runner:
    def __init__(self, client, out_dir):
        self.client = client
        self.out_dir = out_dir
        self.images = 0

    def tool(self, name, args, timeout=600):
        result = self.client.request("tools/call", {"name": name, "arguments": args}, timeout)
        parts, failed = [], bool((result or {}).get("isError"))
        for item in (result or {}).get("content", []):
            if item.get("type") == "text":
                text = item.get("text", "")
                try:
                    parts.append(json.loads(text))
                except ValueError:
                    parts.append(text)
            elif item.get("type") == "image":
                self.images += 1
                ext = (item.get("mimeType") or "image/png").split("/")[-1]
                path = os.path.join(self.out_dir, f"figcraft-{self.images:03d}.{ext}")
                with open(path, "wb") as f:
                    f.write(base64.b64decode(item.get("data", "")))
                parts.append({"image": path})
        value = parts[0] if len(parts) == 1 else parts
        if failed:
            raise RuntimeError(json.dumps(mask(value), ensure_ascii=False))
        return value

    def command(self, command, params=None):
        return self.tool("run_command", {"command": command, "params": params or {}})

    def peers(self):
        value = self.command("collab.peers")
        return value if isinstance(value, dict) else {}

    def wait_live(self, timeout):
        deadline = time.monotonic() + timeout
        while True:
            state = self.peers()
            status = state.get("status")
            if status == "live":
                return state
            if status == "failed":
                raise RuntimeError(f"join failed: {state.get('reason') or 'no reason given'}")
            # a join that failed can show only in `sessions` of the document it was started from
            for s in state.get("sessions") or []:
                if s.get("status") == "failed":
                    raise RuntimeError(f"join failed: {s.get('reason') or 'no reason given'}")
            if time.monotonic() > deadline:
                raise TimeoutError(f"still {status!r} after {timeout} s")
            time.sleep(1.0)

    def sync(self, seconds):
        end = time.monotonic() + seconds
        state = self.peers()
        while time.monotonic() < end:
            time.sleep(0.5)
            state = self.peers()
        if state.get("status") not in ("live", "off"):
            raise RuntimeError(f"session is {state.get('status')!r}: {state.get('reason')}")
        return {k: state.get(k) for k in ("status", "role", "access", "readOnly", "fileName")} | {
            "peers": len(state.get("peers") or [])}

    def step(self, step):
        if "join" in step:
            started = self.command("collab.join", {"link": step["join"]})
            state = self.wait_live(float(step.get("timeout", 180)))
            return "join", {"started": started, "session": {k: state.get(k) for k in (
                "status", "role", "access", "readOnly", "fileName", "pages", "hosting")}}
        if "command" in step:
            return "command", self.command(step["command"], step.get("params"))
        if "tool" in step:
            return "tool", self.tool(step["tool"], step.get("args") or {},
                                     float(step.get("timeout", 600)))
        if "sync" in step:
            return "sync", self.sync(float(step["sync"]))
        if step.get("leave"):
            return "leave", self.command("collab.leave")
        raise ValueError(f"unknown step: {json.dumps(step, ensure_ascii=False)[:200]}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("steps", help="JSON file with an array of steps")
    ap.add_argument("--cli", required=True, help="path to figcraft-cli")
    ap.add_argument("--out-dir", default=".", help="where images returned by tools are written")
    ap.add_argument("--stderr-log", default="figcraft-mcp.stderr.log")
    ap.add_argument("--show-secrets", action="store_true",
                    help="print share links, join codes and owner tokens as they are")
    args = ap.parse_args()

    try:
        with open(args.steps, encoding="utf-8") as f:
            steps = json.load(f)
        assert isinstance(steps, list), "steps must be a JSON array"
    except Exception as e:  # noqa: BLE001
        print(json.dumps({"ok": False, "error": f"bad steps file: {e}"}), flush=True)
        return 2
    os.makedirs(args.out_dir, exist_ok=True)

    # No --automation-*-root: a confined session can't reach any server.
    client = McpClient([args.cli, "mcp"], args.stderr_log)
    try:
        init = client.request("initialize", {
            "protocolVersion": PROTOCOL, "capabilities": {},
            "clientInfo": {"name": "figcraft_live.py", "version": "1"}}, timeout=60)
        client.notify("notifications/initialized")
    except Exception as e:  # noqa: BLE001
        print(json.dumps({"ok": False, "error": f"MCP startup failed: {e}"}), flush=True)
        client.close()
        return 2
    print(json.dumps({"ok": True, "server": (init or {}).get("serverInfo")}), flush=True)

    runner, code = Runner(client, args.out_dir), 0
    for i, step in enumerate(steps):
        try:
            kind, result = runner.step(step)
            out = {"step": i, "kind": kind, "ok": True, "result": result}
        except Exception as e:  # noqa: BLE001
            out = {"step": i, "ok": False, "error": str(e)}
            code = 1
        if not args.show_secrets:
            out = mask(out)
        print(json.dumps(out, ensure_ascii=False), flush=True)
        if code:
            break
    client.close()
    return code


if __name__ == "__main__":
    sys.exit(main())
