#!/usr/bin/env node
// 深清手机端 · 桥接协议一致性检查（v1）
// 用法：node tools/conformance.mjs --base http://127.0.0.1:17777 --token demo-token
// 对真实插件或 tools/mock_bridge.mjs 均可运行；退出码 0 = 通过。
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");

function arg(name, fallback) {
  const index = process.argv.indexOf("--" + name);
  if (index >= 0 && process.argv[index + 1]) return process.argv[index + 1];
  return fallback;
}
const BASE = String(arg("base", "http://127.0.0.1:17777")).replace(/\/$/, "");
const TOKEN = String(arg("token", "demo-token"));
const THEME_PATH = String(arg("theme", path.join(ROOT, "theme", "tokens.json")));
const ALLOW_UNCONFIGURED = process.argv.includes("--allow-unconfigured");
const tokens = JSON.parse(fs.readFileSync(THEME_PATH, "utf8"));

let passed = 0;
const failures = [];
function check(name, condition, detail) {
  if (condition) { passed += 1; console.log("  ok   " + name); }
  else { failures.push(name + (detail ? " — " + detail : "")); console.log("  FAIL " + name + (detail ? " — " + detail : "")); }
}
function authHeaders(token) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = "Bearer " + token;
  return headers;
}
async function call(pathname, options, token = TOKEN) {
  const response = await fetch(BASE + pathname, { ...(options || {}), headers: { ...(options?.headers || {}), ...authHeaders(token) } });
  const text = await response.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { /* 非 JSON */ }
  return { response, body, text };
}

function isErrorShape(body) {
  return Boolean(body && body.schema === "shenqing.error/v1" && body.ok === false && body.error && typeof body.error.code === "string" && typeof body.error.message === "string");
}

export async function runConformance() {
  console.log("深清桥接一致性检查：", BASE);
  console.log("1) /ping 无认证");
  {
    const { response, body } = await call("/ping", {}, "");
    check("HTTP 200", response.status === 200, "status " + response.status);
    check("shenqing=true", body?.shenqing === true);
    check("protocol.major=1", body?.protocol?.major === 1, JSON.stringify(body?.protocol));
  }

  console.log("2) /manifest");
  {
    const { response, body } = await call("/shenqing/v1/manifest");
    check("HTTP 200", response.status === 200, "status " + response.status);
    check("schema=shenqing.bridge/v1", body?.schema === "shenqing.bridge/v1", String(body?.schema));
    check("protocol.major=1", body?.protocol?.major === 1);
    check("capabilities 包含 chat/theme/spaces/stats", ["chat", "theme", "spaces", "stats"].every((c) => Array.isArray(body?.capabilities) && body.capabilities.includes(c)));
    check("theme.tokenVersion=1", body?.theme?.tokenVersion === 1);
    check("plugin 有 id/version", typeof body?.plugin?.id === "string" && typeof body?.plugin?.version === "string");
  }

  console.log("3) /theme 与 theme/tokens.json 逐值一致");
  for (const mode of Object.keys(tokens.modes)) {
    const { response, body } = await call("/shenqing/v1/theme?mode=" + encodeURIComponent(mode));
    check(mode + " HTTP 200", response.status === 200, "status " + response.status);
    check(mode + " tokenVersion=1", body?.tokenVersion === 1);
    const expected = tokens.modes[mode].colors;
    const actual = body?.colors || {};
    const keys = Object.keys(actual);
    check(mode + " 至少返回核心颜色", ["bgTop", "textPrimary", "accent", "cardBg"].every((k) => typeof actual[k] === "string"));
    const diffs = keys.filter((k) => expected[k] && actual[k] !== expected[k]);
    check(mode + " 颜色值一致", diffs.length === 0, diffs.slice(0, 5).map((k) => k + "=" + actual[k] + " 期望 " + expected[k]).join(", "));
    check(mode + " 不含未知颜色", keys.every((k) => k in expected), keys.filter((k) => !(k in expected)).join(","));
  }

  console.log("4) /spaces");
  {
    const { response, body } = await call("/shenqing/v1/spaces");
    check("HTTP 200", response.status === 200);
    check("spaces 是数组", Array.isArray(body?.spaces));
    check("每个 space 有 id/name/minutes", Array.isArray(body?.spaces) && body.spaces.every((s) => s && typeof s.id === "string" && typeof s.name === "string" && Number.isFinite(Number(s.minutes))));
    const active = body?.active;
    check("active 为 null 或合法会话", active === null || (typeof active?.remainingSeconds === "number" && active.remainingSeconds >= 0 && typeof active.spaceId === "string"));
  }

  console.log("5) /stats");
  {
    const { response, body } = await call("/shenqing/v1/stats?range=7d");
    check("HTTP 200", response.status === 200);
    check("last7Days 恰好 7 条", Array.isArray(body?.last7Days) && body.last7Days.length === 7, String(body?.last7Days?.length));
    const days = body?.last7Days || [];
    check("日期升序", days.every((d, i) => i === 0 || String(d.date) > String(days[i - 1].date)));
    check("分钟数为非负数", days.every((d) => Number(d.minutes) >= 0));
    check("todayMinutes/weekMinutes 存在", Number.isFinite(Number(body?.todayMinutes)) && Number.isFinite(Number(body?.weekMinutes)));
  }

  console.log("6) /chat");
  {
    const { response, body } = await call("/shenqing/v1/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ schema: "shenqing.chat/v1", conversationId: "cv_conf", messages: [{ role: "user", content: "25 分钟能做什么" }], stream: false }),
    });
    if (response.status === 503 && ALLOW_UNCONFIGURED) {
      check("未配置时返回 503 not_configured", body?.error?.code === "not_configured");
    } else {
      check("HTTP 200", response.status === 200, "status " + response.status);
      check("ok=true", body?.ok === true);
      check("message.content 非空", typeof body?.message?.content === "string" && body.message.content.length > 0);
    }
    check("错误响应绝不回显配对令牌", !JSON.stringify(body || "").includes(TOKEN || "__none__"));
  }

  console.log("7) CORS 预检");
  {
    const response = await fetch(BASE + "/shenqing/v1/manifest", { method: "OPTIONS", headers: { Origin: "http://192.168.1.9:5173", "Access-Control-Request-Method": "GET", "Access-Control-Request-Headers": "authorization" } });
    const allowOrigin = response.headers.get("access-control-allow-origin");
    const allowHeaders = String(response.headers.get("access-control-allow-headers") || "").toLowerCase();
    check("预检 2xx", response.status >= 200 && response.status < 300, "status " + response.status);
    check("Access-Control-Allow-Origin: *", allowOrigin === "*", String(allowOrigin));
    check("允许 Authorization 头", allowHeaders.includes("authorization"), allowHeaders);
  }

  console.log("8) 错误体与 401");
  {
    const { response, body } = await call("/shenqing/v1/does-not-exist");
    check("404 错误体符合 shenqing.error/v1", response.status === 404 && isErrorShape(body), "status " + response.status);
    if (TOKEN) {
      const noAuth = await call("/shenqing/v1/manifest", {}, "");
      check("无令牌返回 401 + error 体", noAuth.response.status === 401 && isErrorShape(noAuth.body), "status " + noAuth.response.status);
    }
  }

  console.log("");
  console.log("通过 " + passed + " 项" + (failures.length ? "，失败 " + failures.length + " 项" : "，全部通过"));
  if (failures.length) {
    for (const failure of failures) console.log(" - " + failure);
    return 1;
  }
  return 0;
}

const isMain = process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url;
if (isMain) {
  runConformance().then((code) => process.exit(code)).catch((error) => {
    console.error("一致性检查异常：", error);
    process.exit(2);
  });
}
