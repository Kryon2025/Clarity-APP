#!/usr/bin/env python3
"""从桌面 AppTheme.qml 生成 Android 主题令牌（单一事实源）。

输出：
- theme/tokens.json（协议 / 文档 / 桌面插件共用）
- app/src/main/assets/theme_tokens.json（Android 运行时读取）

用法：
    python tools/build_theme_tokens.py
    python tools/build_theme_tokens.py --check
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
DEFAULT_QML = WORKSPACE / "深清" / "src" / "shenqing" / "qml" / "theme" / "AppTheme.qml"
TOKENS_JSON = ROOT / "theme" / "tokens.json"
ANDROID_ASSET = ROOT / "app" / "src" / "main" / "assets" / "theme_tokens.json"
MODES = ["solid", "light", "glass", "ocean"]
SKIP = {"mode", "fontScale", "fontFamily", "navMode", "customPalette"}
NL = chr(10)


def balance(text: str) -> int:
    depth = 0
    for ch in text:
        if ch in "[{(":
            depth += 1
        elif ch in "]})":
            depth -= 1
    return depth


def strip_comment(line: str) -> str:
    in_string = False
    index = 0
    while index < len(line):
        ch = line[index]
        if ch == '"' and (index == 0 or line[index - 1] != chr(92)):
            in_string = not in_string
        if not in_string and ch == "/" and line[index + 1:index + 2] == "/":
            return line[:index]
        index += 1
    return line


def parse_properties(text: str):
    lines = text.splitlines()
    props = []
    i = 0
    while i < len(lines):
        line = strip_comment(lines[i]).strip()
        i += 1
        if line.startswith("readonly property "):
            line = line[len("readonly property "):]
        elif line.startswith("property "):
            line = line[len("property "):]
        else:
            continue
        kind, _, rest = line.partition(" ")
        name, sep, expr = rest.partition(":")
        if not sep:
            continue
        kind = kind.strip()
        name = name.strip()
        expr = expr.strip()
        if kind not in ("color", "var") or name in SKIP:
            continue
        while i < len(lines):
            nxt = strip_comment(lines[i]).strip()
            if balance(expr) > 0 or nxt[:1] in (":", "?"):
                expr += NL + nxt
                i += 1
            else:
                break
        props.append((kind, name, expr))
    return props


NODE_EVAL = """
const defs = JSON.parse(process.argv[1]);
const mode = process.argv[2];
const state = { solid: mode === "solid", light: mode === "light", glass: mode === "glass", ocean: mode === "ocean", custom: false, customLight: false };
const cache = Object.create(null);
const ctx = new Proxy({}, {
  has: () => true,
  get(_target, key) {
    if (typeof key !== "string") return undefined;
    if (key in state) return state[key];
    if (key === "Math") return Math;
    if (key === "undefined") return undefined;
    if (!(key in defs)) throw new Error("unknown identifier: " + key);
    if (key in cache) return cache[key];
    const fn = new Function("ctx", "with (ctx) { return (" + defs[key] + "); }");
    const value = fn(ctx);
    cache[key] = value;
    return value;
  },
});
const out = {};
for (const name of Object.keys(defs)) out[name] = ctx[name];
console.log(JSON.stringify(out));
"""


def evaluate(defs: dict, mode: str) -> dict:
    proc = subprocess.run(
        ["node", "-e", NODE_EVAL, json.dumps(defs, ensure_ascii=False), mode],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if proc.returncode != 0:
        raise SystemExit("Node 求值主题失败（" + mode + "）：" + NL + proc.stderr + NL + proc.stdout)
    return json.loads(proc.stdout)


def build(qml: Path) -> dict:
    text = qml.read_text(encoding="utf-8")
    props = parse_properties(text)
    if not props:
        raise SystemExit("没有在 " + str(qml) + " 里找到任何 color / var 属性")
    defs = {name: expr for _kind, name, expr in props}

    modes = {}
    for mode in MODES:
        values = evaluate(defs, mode)
        colors = {}
        lists = {}
        for _kind, name, _expr in props:
            value = values[name]
            if isinstance(value, list):
                lists[name] = value
            elif isinstance(value, str) and value.startswith("#"):
                colors[name] = value
            else:
                raise SystemExit(name + " 的值不是颜色或数组：" + repr(value))
        modes[mode] = {"colors": colors, "lists": lists}

    return {
        "schema": "shenqing.theme-tokens/v1",
        "tokenVersion": 1,
        "source": {
            "file": qml.relative_to(WORKSPACE).as_posix(),
            "sha256": hashlib.sha256(qml.read_bytes()).hexdigest(),
        },
        "font": {"scale": 1, "family": "", "range": [0.8, 1.4]},
        "modes": modes,
    }


def dump(tokens: dict) -> str:
    return json.dumps(tokens, ensure_ascii=False, indent=2) + NL


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--qml", type=Path, default=DEFAULT_QML)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.qml.exists():
        raise SystemExit("找不到 AppTheme.qml：" + str(args.qml))

    text = dump(build(args.qml))
    if args.check:
        problems = []
        if not TOKENS_JSON.exists() or TOKENS_JSON.read_text(encoding="utf-8") != text:
            problems.append(str(TOKENS_JSON))
        if not ANDROID_ASSET.exists() or ANDROID_ASSET.read_text(encoding="utf-8") != text:
            problems.append(str(ANDROID_ASSET))
        if problems:
            print("主题令牌已漂移，请运行 python tools/build_theme_tokens.py 重新生成：")
            for item in problems:
                print(" -", item)
            return 1
        print("主题令牌同步：%d 个颜色 x %d 模式" % (len(json.loads(text)["modes"]["solid"]["colors"]), len(MODES)))
        return 0

    TOKENS_JSON.parent.mkdir(parents=True, exist_ok=True)
    ANDROID_ASSET.parent.mkdir(parents=True, exist_ok=True)
    TOKENS_JSON.write_text(text, encoding="utf-8")
    ANDROID_ASSET.write_text(text, encoding="utf-8")
    print("已生成 " + str(TOKENS_JSON) + " 与 " + str(ANDROID_ASSET))
    return 0


if __name__ == "__main__":
    sys.exit(main())
