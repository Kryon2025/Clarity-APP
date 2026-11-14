#!/usr/bin/env node
// 深清手机端 · 模拟桌面桥接（协议 v1）
// 用途：手机端开发/演示、协议一致性基准。真实实现见 深清插件开发/com.shenqing.aichat。
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const WEB = path.join(ROOT, "web");
const tokens = JSON.parse(fs.readFileSync(path.join(ROOT, "theme", "tokens.json"), "utf8"));

function arg(name, fallback) {
  const index = process.argv.indexOf("--" + name);
  if (index >= 0 && process.argv[index + 1]) return process.argv[index + 1];
  return fallback;
}
const PORT = Number(arg("port", "17777"));
const TOKEN = String(arg("token", "demo-token"));
const NO_AI = process.argv.includes("--no-ai");
const HOST = arg("host", "127.0.0.1");
const START = Date.now();

const spaces = [
  { id: "sp_light", name: "工作学习", minutes: 45, tier: "light", atmosphere: "秩序", allowed_apps: ["wechat.exe"], super: false },
  { id: "sp_cover", name: "写点什么", minutes: 60, tier: "cover", atmosphere: "静谧", allowed_apps: ["typora.exe"], super: false },
  { id: "sp_deep", name: "深度工作", minutes: 90, tier: "deep", atmosphere: "专注", allowed_apps: [], super: true },
  { id: "sp_sleep", name: "睡前收心", minutes: 480, tier: "sleep", atmosphere: "夜色", allowed_apps: [], super: false },
];

function dayKey(date) {
  return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
}
const sample = [85, 50, 65, 0, 120, 90, 30];
const last7Days = [];
for (let i = 6; i >= 0; i--) {
  const date = new Date();
  date.setDate(date.getDate() - i);
  last7Days.push({ date: dayKey(date), minutes: sample[6 - i] ?? 0 });
}

const state = {
  active: null,
  conversations: [
    {
      id: "cv_mock_1",
      title: "桌面连接后的第一条",
      updatedAt: new Date().toISOString(),
      messages: [
        { role: "user", content: "你已经在桌面上了吗？", at: START - 60000 },
        { role: "assistant", content: "在。手机通过局域网连到这里，Key 仍然只留在电脑上。", at: START - 59000 },
      ],
    },
  ],
};

function stats() {
  return {
    schema: "shenqing.stats/v1",
    todayMinutes: last7Days[last7Days.length - 1].minutes,
    weekMinutes: last7Days.reduce((sum, day) => sum + day.minutes, 0),
    streakDays: 6,
    sessionCount: 42,
    last7Days,
    categories: [
      { name: "工作学习", minutes: 320, color: "#2ED3C6" },
      { name: "写作", minutes: 180, color: "#7FB6FF" },
    ],
  };
}

function activeView() {
  if (!state.active) return null;
  return { ...state.active, remainingSeconds: Math.max(0, state.active.remainingSeconds) };
}

function mockReply(text) {
  if (/总结|回顾/.test(text)) {
    return { content: "今天已经专注 120 分钟。建议只写三行：做完了什么、卡在哪里、明天第一件事。", reasoning: "来自桌面桥接的真实会话（模拟回复）。" };
  }
  if (/25|番茄/.test(text)) {
    return { content: "25 分钟只做一件事。把手机翻面，先写三行最丑的草稿。四轮后再休息 15 分钟。", reasoning: "先把开始成本降到最低。" };
  }
  return { content: "先挑一个 15 分钟能推进的动作，开一段浅清。做完再决定下一步。", reasoning: "" };
}

function send(res, status, body, extra) {
  const text = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(text),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Cache-Control": "no-store",
    ...(extra || {}),
  });
  res.end(text);
}
function error(res, status, code, message) {
  send(res, status, { schema: "shenqing.error/v1", ok: false, error: { code, message } });
}
function authorized(req) {
  if (!TOKEN) return true;
  const value = String(req.headers.authorization || "");
  return value === "Bearer " + TOKEN;
}
function readBody(req) {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (chunk) => { data += chunk; if (data.length > 1024 * 1024) req.destroy(); });
    req.on("end", () => { try { resolve(data ? JSON.parse(data) : {}); } catch { resolve(null); } });
  });
}
function serveStatic(req, res, pathname) {
  let relative = pathname === "/" ? "/index.html" : pathname;
  const file = path.resolve(path.join(WEB, relative));
  if (!file.startsWith(WEB) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) return false;
  const ext = path.extname(file);
  const types = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8", ".svg": "image/svg+xml", ".webmanifest": "application/manifest+json" };
  res.writeHead(200, { "Content-Type": types[ext] || "application/octet-stream", "Cache-Control": "no-store" });
  fs.createReadStream(file).pipe(res);
  return true;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", "http://" + (req.headers.host || "localhost"));
  const pathname = decodeURIComponent(url.pathname);
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Authorization, Content-Type",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Max-Age": "86400",
    });
    res.end();
    return;
  }
  if (pathname === "/ping") {
    send(res, 200, { shenqing: true, protocol: { major: 1, minor: 0 }, app: "深清（模拟桥接）" });
    return;
  }
  if (pathname.startsWith("/shenqing/v1/")) {
    if (!authorized(req)) { error(res, 401, "unauthorized", "配对令牌无效"); return; }
  }
  if (pathname === "/shenqing/v1/manifest") {
    send(res, 200, {
      schema: "shenqing.bridge/v1",
      protocol: { major: 1, minor: 0 },
      host: { name: "深清", version: "0.17.0", platform: "windows" },
      plugin: { id: "com.shenqing.aichat", name: "AI 沟通（模拟）", version: "1.0.0", api_version: "0.1" },
      capabilities: ["chat", "history", "spaces", "stats", "theme", "plugins", "session.control"],
      theme: { modes: ["solid", "light", "glass", "ocean"], defaultMode: "solid", tokenVersion: 1 },
      endpoints: {
        theme: "/shenqing/v1/theme",
        spaces: "/shenqing/v1/spaces",
        stats: "/shenqing/v1/stats",
        chat: "/shenqing/v1/chat",
        conversations: "/shenqing/v1/conversations",
      },
      serverTime: new Date().toISOString(),
    });
    return;
  }
  if (pathname === "/shenqing/v1/theme") {
    const requested = url.searchParams.get("mode") || "solid";
    const mode = tokens.modes[requested] ? requested : "solid";
    send(res, 200, {
      schema: "shenqing.theme/v1",
      tokenVersion: 1,
      mode,
      colors: tokens.modes[mode].colors,
      lists: tokens.modes[mode].lists,
      font: tokens.font,
    });
    return;
  }
  if (pathname === "/shenqing/v1/spaces") {
    send(res, 200, { schema: "shenqing.spaces/v1", spaces, active: activeView() });
    return;
  }
  if (pathname === "/shenqing/v1/stats") {
    send(res, 200, stats());
    return;
  }
  if (pathname === "/shenqing/v1/chat" && req.method === "POST") {
    const body = await readBody(req);
    if (!body || !Array.isArray(body.messages)) { error(res, 400, "bad_request", "messages 必须是数组"); return; }
    if (NO_AI) { error(res, 503, "not_configured", "桌面 AI 未配置"); return; }
    const last = [...body.messages].reverse().find((m) => m && m.role === "user");
    const reply = mockReply(String(last?.content || ""));
    const conversationId = String(body.conversationId || "cv_mock");
    let conversation = state.conversations.find((c) => c.id === conversationId);
    if (!conversation) {
      conversation = { id: conversationId, title: String(last?.content || "新对话").slice(0, 18), updatedAt: new Date().toISOString(), messages: [] };
      state.conversations.unshift(conversation);
    }
    conversation.messages.push({ role: "assistant", content: reply.content, reasoning: reply.reasoning, at: Date.now() });
    conversation.updatedAt = new Date().toISOString();
    send(res, 200, {
      schema: "shenqing.chat/v1",
      ok: true,
      conversationId,
      message: { role: "assistant", content: reply.content, reasoning: reply.reasoning, tokens: 96 },
      usage: { prompt_tokens: 42, completion_tokens: 96, cost: 0.0001, currency: "CNY" },
    });
    return;
  }
  if (pathname === "/shenqing/v1/conversations") {
    send(res, 200, { schema: "shenqing.conversations/v1", conversations: state.conversations.map((c) => ({ id: c.id, title: c.title, updatedAt: c.updatedAt })) });
    return;
  }
  if (pathname.startsWith("/shenqing/v1/conversations/")) {
    const id = pathname.slice("/shenqing/v1/conversations/".length);
    const conversation = state.conversations.find((c) => c.id === id);
    if (!conversation) { error(res, 404, "not_found", "会话不存在"); return; }
    send(res, 200, { schema: "shenqing.conversation/v1", id: conversation.id, title: conversation.title, messages: conversation.messages });
    return;
  }
  if (pathname === "/shenqing/v1/plugins") {
    send(res, 200, {
      schema: "shenqing.plugins/v1",
      plugins: [{ id: "com.shenqing.aichat", name: "AI 沟通（模拟）", version: "1.0.0", enabled: true, error: "", navItems: [{ itemId: "chat", title: "AI 沟通" }] }],
    });
    return;
  }
  if (pathname === "/shenqing/v1/sessions/start" && req.method === "POST") {
    const body = await readBody(req);
    const space = spaces.find((s) => s.id === body?.spaceId);
    if (!space) { error(res, 404, "not_found", "空间不存在"); return; }
    const total = space.minutes * 60;
    state.active = {
      spaceId: space.id, name: space.name, tier: space.tier, remainingSeconds: total, totalSeconds: total,
      paused: false, canPause: space.tier === "light", locked: space.tier === "cover" || space.tier === "deep",
      startedAt: new Date().toISOString(),
    };
    send(res, 200, { ok: true, active: state.active });
    return;
  }
  if (pathname === "/shenqing/v1/sessions/stop" && req.method === "POST") {
    const body = await readBody(req);
    if (state.active && state.active.locked && !body?.force) { error(res, 409, "locked", "深清期间不可提前结束"); return; }
    state.active = null;
    send(res, 200, { ok: true, active: null });
    return;
  }
  if (req.method === "GET" && serveStatic(req, res, pathname)) return;
  error(res, 404, "not_found", "没有这个接口：" + pathname);
});

server.listen(PORT, HOST, () => {
  console.log("深清模拟桥接：http://" + HOST + ":" + PORT + "  token=" + TOKEN);
  console.log("手机端：浏览器打开同一地址即可（若 web/ 存在）");
});

// 供一致性测试/集成测试导入（导入即监听；用完 server.close()）
export { server, state, spaces, tokens };
