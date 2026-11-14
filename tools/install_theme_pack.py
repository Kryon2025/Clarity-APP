#!/usr/bin/env python3
"""把 .sqtheme 主题包装到桌面深清（settings.json 的 custom_themes）。

手机上导入同一文件即可得到相同配色；桌面端深清 0.18+ 已支持
theme_mode = "custom:<id>" 与 custom_themes 动态调色板。

用法（先退出深清，避免被运行中的设置覆盖）：
    python tools/install_theme_pack.py themes/aurora.sqtheme
    python tools/install_theme_pack.py themes/aurora.sqtheme --no-activate
    python tools/install_theme_pack.py themes/aurora.sqtheme --data-dir D:/ShenQing
    python tools/install_theme_pack.py themes/aurora.sqtheme --check
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import time
from pathlib import Path

PALETTE_KEYS = ("bgTop", "bgMid", "bgBottom", "accent", "cardBg", "textPrimary")
HEX = set("0123456789abcdefABCDEF")


def is_hex(value: object) -> bool:
    text = str(value or "").strip()
    return len(text) == 7 and text.startswith("#") and all(ch in HEX for ch in text[1:])


def load_pack(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise SystemExit("主题包必须是 JSON 对象")
    if str(data.get("schema") or "") not in ("shenqing.theme-pack/v1", ""):
        raise SystemExit("schema 必须是 shenqing.theme-pack/v1")
    theme_id = str(data.get("id") or "").strip()
    if not theme_id or any(ch not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-" for ch in theme_id):
        raise SystemExit("主题包 id 只能包含字母、数字、点、下划线、短横线")
    palette: dict[str, str] = {}
    raw_palette = data.get("palette")
    if isinstance(raw_palette, dict):
        palette.update({key: str(raw_palette.get(key) or "") for key in PALETTE_KEYS})
    modes = data.get("modes")
    if isinstance(modes, dict):
        solid = modes.get("solid") if isinstance(modes.get("solid"), dict) else {}
        colors = solid.get("colors") if isinstance(solid.get("colors"), dict) else {}
        for key in PALETTE_KEYS:
            if not palette.get(key) and is_hex(colors.get(key)):
                palette[key] = str(colors[key])
    platforms = data.get("platforms")
    if platforms is not None and not isinstance(platforms, list):
        raise SystemExit("platforms 必须是数组，例如 [\"desktop\", \"mobile\"]")
    if isinstance(platforms, list):
        platforms = [str(item).strip().lower() for item in platforms if str(item).strip().lower() in ("desktop", "mobile")]
        if platforms and "desktop" not in platforms:
            raise SystemExit("该主题只声明了手机端（platforms 不含 desktop），不能装到桌面")
    else:
        platforms = ["desktop"]
    missing = [key for key in PALETTE_KEYS if not is_hex(palette.get(key))]
    if missing:
        raise SystemExit("主题包缺少合法的 palette 字段：" + "、".join(missing))
    return {
        "id": theme_id,
        "name": str(data.get("name") or theme_id)[:40],
        "description": str(data.get("description") or "")[:200],
        "palette": {key: palette[key] for key in PALETTE_KEYS},
        "platforms": platforms,
    }


def settings_path(data_dir: Path) -> Path:
    return data_dir / "settings.json"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("pack", type=Path)
    parser.add_argument("--data-dir", default="")
    parser.add_argument("--no-activate", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)

    pack = load_pack(args.pack)
    print("主题包：" + pack["name"] + " (" + pack["id"] + ")")
    print("platforms：" + "、".join(pack["platforms"]))
    print("palette：" + json.dumps(pack["palette"], ensure_ascii=False))
    if args.check:
        print("校验通过")
        return 0

    appdata = args.data_dir.strip() or os.environ.get("SHENQING_DATA_DIR") or os.environ.get("APPDATA")
    if not appdata:
        raise SystemExit("找不到 %APPDATA%，请用 --data-dir 指定深清数据目录")
    data_dir = Path(appdata)
    if data_dir.name.lower() != "shenqing" and (data_dir / "settings.json").exists() is False:
        # 允许直接传 %APPDATA%\ShenQing
        data_dir = data_dir / "ShenQing"
    target = settings_path(data_dir)
    if not target.exists():
        raise SystemExit("找不到 " + str(target) + "，请先启动一次深清")
    data = json.loads(target.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise SystemExit("settings.json 不是对象")

    backup = target.with_name("settings.json.bak-theme-" + time.strftime("%Y%m%d-%H%M%S"))
    shutil.copy2(target, backup)
    themes = data.get("custom_themes")
    if not isinstance(themes, dict):
        themes = {}
    themes[pack["id"]] = {
        "name": pack["name"],
        "description": pack["description"],
        "palette": pack["palette"],
    }
    data["custom_themes"] = themes
    if not args.no_activate:
        data["theme_mode"] = "custom:" + pack["id"]
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print("已安装到 " + str(target) + "（备份 " + backup.name + "）")
    if not args.no_activate:
        print("已切换 theme_mode = custom:" + pack["id"] + "，重启深清生效")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
