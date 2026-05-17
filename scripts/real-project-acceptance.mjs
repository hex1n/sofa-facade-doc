#!/usr/bin/env node

const baseUrl = trimSlash(process.env.SOFA_DOC_BASE_URL || process.argv[2] || "http://127.0.0.1:8080");
const token = process.env.SOFA_DOC_TOKEN || process.argv[3] || "";
const projectId = process.env.SOFA_DOC_PROJECT || process.argv[4] || "";
const branchName = process.env.SOFA_DOC_BRANCH || process.argv[5] || "";
const baselineBranch = process.env.SOFA_DOC_BASELINE || "";
const serviceFqn = process.env.SOFA_DOC_SERVICE || "";
const methodId = process.env.SOFA_DOC_METHOD || "";
const scanBranch = process.env.SOFA_DOC_ACCEPTANCE_SCAN === "1";
const scanProject = process.env.SOFA_DOC_ACCEPTANCE_SCAN_ALL === "1";
const probeRpc = process.env.SOFA_DOC_ACCEPTANCE_PROBE === "1";
const invokeRpc = process.env.SOFA_DOC_ACCEPTANCE_INVOKE === "1";
const requireRpcSuccess = process.env.SOFA_DOC_REQUIRE_RPC === "1";
const caseCheck = process.env.SOFA_DOC_ACCEPTANCE_CASE === "1";
const format = process.env.SOFA_DOC_ACCEPTANCE_FORMAT || "md";
const invokeArgsJson = process.env.SOFA_DOC_ARGS_JSON || "";

const checks = [];
const evidence = {};

function trimSlash(value) {
  return value.replace(/\/+$/, "");
}

function encode(value) {
  return encodeURIComponent(value);
}

function now() {
  return new Date().toISOString();
}

function addCheck(name, ok, detail = "") {
  checks.push({ name, ok: !!ok, detail });
}

function requireValue(value, message) {
  if (!value) throw new Error(message);
  return value;
}

function short(value, limit = 180) {
  const text = String(value == null ? "" : value).replace(/\s+/g, " ").trim();
  return text.length > limit ? `${text.slice(0, limit)}...` : text;
}

async function http(path, options = {}) {
  const headers = Object.assign({}, options.headers || {});
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
  const response = await fetch(`${baseUrl}${path}`, Object.assign({}, options, { headers }));
  const body = await response.text();
  if (!response.ok) {
    const message = `${options.method || "GET"} ${path} failed: ${response.status} ${short(body, 500)}`;
    throw new Error(message);
  }
  return body;
}

async function json(path, options = {}) {
  return JSON.parse(await http(path, options));
}

async function tryStep(name, fn, options = {}) {
  try {
    const result = await fn();
    addCheck(name, true, options.detail ? options.detail(result) : short(JSON.stringify(result)));
    return result;
  } catch (error) {
    addCheck(name, false, error.message || String(error));
    if (!options.optional) throw error;
    return null;
  }
}

function pickMethod(services) {
  for (const service of services) {
    if (serviceFqn && service.fqn !== serviceFqn) continue;
    for (const method of service.methods || []) {
      if (methodId && method.id !== methodId) continue;
      return { service, method };
    }
  }
  const wanted = [serviceFqn ? `service=${serviceFqn}` : "", methodId ? `method=${methodId}` : ""].filter(Boolean).join(", ");
  throw new Error(`no method found${wanted ? ` for ${wanted}` : ""}`);
}

function markdownReport(summary) {
  const rows = checks.map(check => `| ${escapeTable(check.name)} | ${check.ok ? "通过" : "失败"} | ${escapeTable(check.detail)} |`).join("\n");
  return [
    "# SOFABoot Facade 真实项目验收记录",
    "",
    `- 时间：${summary.time}`,
    `- 平台：${summary.baseUrl}`,
    `- 项目：${summary.project}`,
    `- 分支：${summary.branch}`,
    `- 基线：${summary.baseline || "-"}`,
    `- 服务：${summary.service || "-"}`,
    `- 方法：${summary.method || "-"}`,
    `- Commit：${summary.commit || "-"}`,
    "",
    "| 验收项 | 结果 | 证据 |",
    "| --- | --- | --- |",
    rows,
    "",
    "## 原始证据",
    "",
    "```json",
    JSON.stringify(evidence, null, 2),
    "```"
  ].join("\n");
}

function escapeTable(value) {
  return String(value == null ? "" : value).replace(/\|/g, "\\|").replace(/\n/g, "<br>");
}

async function main() {
  requireValue(token, "SOFA_DOC_TOKEN or argv[3] is required");
  requireValue(projectId, "SOFA_DOC_PROJECT or argv[4] is required");
  requireValue(branchName, "SOFA_DOC_BRANCH or argv[5] is required");
  if (invokeRpc && !invokeArgsJson) throw new Error("SOFA_DOC_ACCEPTANCE_INVOKE=1 requires SOFA_DOC_ARGS_JSON");

  evidence.health = await tryStep("健康检查", () => json("/api/health"), {
    detail: result => result.status || JSON.stringify(result)
  });

  evidence.projects = await tryStep("项目可见", async () => {
    const projects = await json("/api/projects");
    const project = projects.find(item => item.id === projectId);
    if (!project) throw new Error(`project ${projectId} is not visible`);
    return project;
  }, { detail: project => `displayName=${project.displayName || project.id}` });

  evidence.branches = await tryStep("分支匹配", async () => {
    const branches = await json(`/api/projects/${encode(projectId)}/branches`);
    if (!branches.includes(branchName)) throw new Error(`branch ${branchName} is not matched; got ${branches.join(", ")}`);
    return branches;
  }, { detail: branches => `matched=${branches.join(", ")}` });

  if (scanProject) {
    evidence.projectScan = await tryStep("项目级扫描", () => json(`/api/projects/${encode(projectId)}/scan`, { method: "POST" }), {
      detail: result => Object.entries(result).map(([branch, item]) => `${branch}:${item.status}`).join(", ")
    });
  }

  if (scanBranch) {
    evidence.branchScan = await tryStep("单分支扫描", () => json(`/api/projects/${encode(projectId)}/branches/scan?branch=${encode(branchName)}`, { method: "POST" }), {
      detail: result => `${result.status}${result.snapshotCreated ? ", snapshotCreated" : ""}${result.message ? `, ${result.message}` : ""}`
    });
  }

  evidence.services = await tryStep("接口列表", async () => {
    const services = await json(`/api/projects/${encode(projectId)}/branches/services?branch=${encode(branchName)}`);
    if (!Array.isArray(services) || !services.length) throw new Error("no service in latest snapshot");
    return services;
  }, { detail: services => `${services.length} services` });

  const selected = pickMethod(evidence.services);
  evidence.selected = {
    service: selected.service.fqn,
    method: selected.method.name,
    methodId: selected.method.id
  };

  evidence.methodDetail = await tryStep("方法详情和字段结构", async () => {
    const detail = await json(`/api/projects/${encode(projectId)}/methods/${encode(selected.method.id)}?branch=${encode(branchName)}`);
    if (!detail.snapshot || detail.snapshot.branch !== branchName) throw new Error("method detail returned a different branch");
    if (!detail.service || detail.service.fqn !== selected.service.fqn) throw new Error("method detail returned a different service");
    if (!detail.method || detail.method.id !== selected.method.id) throw new Error("method detail returned a different method");
    return detail;
  }, { detail: detail => `commit=${detail.snapshot.commit || "-"}, params=${(detail.method.params || []).length}, return=${detail.method.returnType || "void"}` });

  evidence.markdown = await tryStep("Markdown 文档", async () => {
    const markdown = await http(`/api/projects/${encode(projectId)}/methods/${encode(selected.method.id)}/markdown?branch=${encode(branchName)}`);
    if (!markdown.includes(selected.service.fqn)) throw new Error("markdown missing service FQN");
    if (!markdown.includes(evidence.methodDetail.method.name)) throw new Error("markdown missing method name");
    if (evidence.methodDetail.runtime && evidence.methodDetail.runtime.directUrl && markdown.includes(evidence.methodDetail.runtime.directUrl)) {
      throw new Error("markdown contains runtime directUrl");
    }
    return { length: markdown.length, head: markdown.split(/\r?\n/).slice(0, 3).join(" / ") };
  }, { detail: result => `length=${result.length}, ${result.head}` });

  evidence.search = await tryStep("搜索", async () => {
    const query = evidence.methodDetail.method.name || selected.service.fqn;
    const hits = await json(`/api/projects/${encode(projectId)}/search?q=${encode(query)}`);
    if (!Array.isArray(hits) || !hits.some(hit => hit.methodId === selected.method.id && hit.branch === branchName)) {
      throw new Error(`search did not find selected method by ${query}`);
    }
    return { query, hits: hits.length };
  }, { detail: result => `query=${result.query}, hits=${result.hits}` });

  if (baselineBranch) {
    evidence.diff = await tryStep("Diff", () => json(`/api/projects/${encode(projectId)}/diff?branch=${encode(branchName)}&base=${encode(baselineBranch)}`), {
      optional: true,
      detail: result => `${Array.isArray(result) ? result.length : 0} changes`
    });
  }

  evidence.validation = await tryStep("参数结构校验", () => json(`/api/projects/${encode(projectId)}/methods/${encode(selected.method.id)}/validate?branch=${encode(branchName)}`, {
    method: "POST",
    body: JSON.stringify({ args: evidence.methodDetail.method.requestExample })
  }), {
    detail: result => `ok=${result.ok}, errors=${(result.errors || []).length}, warnings=${(result.warnings || []).length}`
  });

  if (probeRpc) {
    evidence.probe = await tryStep("连通性检查", async () => {
      const result = await json(`/api/projects/${encode(projectId)}/methods/${encode(selected.method.id)}/probe?branch=${encode(branchName)}`);
      if (requireRpcSuccess && !result.reachable) throw new Error(result.error || "probe failed");
      return result;
    }, {
      optional: !requireRpcSuccess,
      detail: result => result ? `reachable=${result.reachable}, latencyMs=${result.latencyMs || "-"}${result.error ? `, ${result.error}` : ""}` : ""
    });
  }

  if (invokeRpc) {
    evidence.invoke = await tryStep("SofaRPC 调用", async () => {
      const parsedArgs = JSON.parse(invokeArgsJson);
      const result = await json(`/api/projects/${encode(projectId)}/methods/${encode(selected.method.id)}/invoke?branch=${encode(branchName)}`, {
        method: "POST",
        body: JSON.stringify({ args: parsedArgs })
      });
      if (requireRpcSuccess && result.status !== "success") throw new Error(result.error || result.message || `invoke status=${result.status}`);
      return result;
    }, {
      optional: !requireRpcSuccess,
      detail: result => result ? `status=${result.status}, elapsedMs=${result.elapsedMs || "-"}` : ""
    });
  }

  if (caseCheck) {
    evidence.case = await tryStep("用例管理", async () => {
      const name = `acceptance-${Date.now()}`;
      const argsJson = JSON.stringify(evidence.methodDetail.method.requestExample, null, 2);
      const created = await json(`/api/projects/${encode(projectId)}/methods/${encode(selected.method.id)}/cases?branch=${encode(branchName)}&service=${encode(selected.service.fqn)}`, {
        method: "POST",
        body: JSON.stringify({ name, argsJson })
      });
      const cases = await json(`/api/projects/${encode(projectId)}/methods/${encode(selected.method.id)}/cases?branch=${encode(branchName)}&service=${encode(selected.service.fqn)}`);
      if (!cases.some(item => item.id === created.id)) throw new Error("created case not listed");
      await json(`/api/projects/${encode(projectId)}/cases/${created.id}`, { method: "DELETE" });
      return { id: created.id, name };
    }, { detail: result => `created and deleted case id=${result.id}` });
  }

  const summary = {
    time: now(),
    baseUrl,
    project: projectId,
    branch: branchName,
    baseline: baselineBranch,
    service: selected.service.fqn,
    method: evidence.methodDetail.method.name,
    methodId: selected.method.id,
    commit: evidence.methodDetail.snapshot.commit,
    ok: checks.every(check => check.ok),
    checks
  };

  if (format === "json") {
    console.log(JSON.stringify({ summary, evidence }, null, 2));
  } else {
    console.log(markdownReport(summary));
  }

  if (!summary.ok) process.exit(1);
}

main().catch(error => {
  console.error(error.message || error);
  process.exit(1);
});
