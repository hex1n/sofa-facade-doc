let token = sessionStorage.getItem("sofaDocToken") || "";
let state = { projects: [], project: "", branch: "", branches: [], baseBranch: "", services: [], serviceFilter: "all", sidebarCollapsed: sessionStorage.getItem("sofaSidebarCollapsed") === "1", invokeCollapsed: sessionStorage.getItem("sofaInvokeCollapsed") === "1", selected: null, markdown: "", selectedCase: null, pendingRoute: null, publishIndex: "", configData: null, configIndex: 0 };

const $ = (id) => document.getElementById(id);

function branchQuery(extra = {}) {
  return new URLSearchParams(Object.assign({ branch: state.branch }, extra)).toString();
}

function servicesQuery() {
  const params = { branch: state.branch };
  if (state.baseBranch && state.baseBranch !== state.branch) params.base = state.baseBranch;
  return new URLSearchParams(params).toString();
}

function invokeQuery(extra = {}) {
  const params = Object.assign({ branch: state.branch }, extra);
  if (state.publishIndex !== "") params.publish = state.publishIndex;
  return new URLSearchParams(params).toString();
}

function authHeaders(extra = {}) {
  return Object.assign({ Authorization: "Bearer " + token }, extra);
}

async function api(path, options = {}) {
  options.headers = authHeaders(options.headers || {});
  const res = await fetch(path, options);
  if (!res.ok) throw new Error(await res.text());
  const type = res.headers.get("content-type") || "";
  return type.includes("application/json") ? res.json() : res.text();
}

function errorMessage(err) {
  const raw = err && err.message ? err.message : String(err || "请求失败");
  try {
    const parsed = JSON.parse(raw);
    return parsed.message || parsed.error || raw;
  } catch (e) {
    return raw;
  }
}

function setProbeResult(message, error = false) {
  $("probeResult").textContent = message;
  $("probeResult").classList.toggle("error-text", error);
}

function renderInvokeResult(result) {
  if (!result || typeof result !== "object") return JSON.stringify(result, null, 2);
  if (result.status === "unreachable") {
    return [
      "调用未发起：目标服务不可连接",
      `目标：${result.targetDirectUrl || "-"}`,
      result.targetUniqueId ? `uniqueId：${result.targetUniqueId}` : "",
      result.error ? `原因：${result.error}` : "",
      "",
      JSON.stringify(result, null, 2)
    ].filter(line => line !== "").join("\n");
  }
  if (result.status === "validation_failed") {
    const errors = (result.validationErrors || []).join("；");
    const warnings = (result.validationWarnings || []).join("；");
    return [
      "调用未发起：请求参数未通过结构校验",
      errors ? `错误：${errors}` : "",
      warnings ? `警告：${warnings}` : "",
      "",
      JSON.stringify(result, null, 2)
    ].filter(line => line !== "").join("\n");
  }
  return JSON.stringify(result, null, 2);
}

function showApp() {
  $("login").classList.add("hidden");
  $("app").classList.remove("hidden");
  applySidebarCollapsed();
  applyInvokeCollapsed();
}

function applySidebarCollapsed() {
  $("app").classList.toggle("sidebar-collapsed", state.sidebarCollapsed);
  const toggle = $("sidebarToggle");
  toggle.textContent = state.sidebarCollapsed ? "›" : "‹";
  const label = state.sidebarCollapsed ? "展开左侧导航" : "收起左侧导航";
  toggle.setAttribute("aria-label", label);
  toggle.setAttribute("title", label);
}

function applyInvokeCollapsed() {
  $("app").classList.toggle("invoke-collapsed", state.invokeCollapsed);
  const toggle = $("invokeToggle");
  toggle.textContent = state.invokeCollapsed ? "‹" : "›";
  const label = state.invokeCollapsed ? "展开 RPC 调用区" : "收起 RPC 调用区";
  toggle.setAttribute("aria-label", label);
  toggle.setAttribute("title", label);
}

function setConfigResult(message, error = false) {
  $("configResult").textContent = message || "";
  $("configResult").classList.toggle("error-text", error);
}

function closeConfigModal() {
  configWorkbench.close();
}

async function openConfigModal() {
  return configWorkbench.open();
}

async function saveConfig() {
  return configWorkbench.save();
}

async function init() {
  if (!token) return;
  showApp();
  state.pendingRoute = Object.fromEntries(new URLSearchParams(location.search));
  state.projects = await api("/api/projects");
  $("projectSelect").innerHTML = state.projects.map(p => `<option value="${p.id}">${p.displayName}</option>`).join("");
  state.project = state.pendingRoute.project && state.projects.some(p => p.id === state.pendingRoute.project) ? state.pendingRoute.project : $("projectSelect").value;
  $("projectSelect").value = state.project;
  await loadBranches();
}

async function loadBranches() {
  const branches = await api(`/api/projects/${state.project}/branches`);
  state.branches = branches;
  $("branchSelect").innerHTML = branches.map(b => `<option value="${b}">${b}</option>`).join("");
  const routedBranch = state.pendingRoute && state.pendingRoute.branch && branches.includes(state.pendingRoute.branch) ? state.pendingRoute.branch : "";
  state.branch = routedBranch || (branches.includes(state.branch) ? state.branch : $("branchSelect").value);
  $("branchSelect").value = state.branch;
  populateBaseBranchSelect();
  clearScanSummary();
  await loadServices();
  await loadScanReports();
}

function populateBaseBranchSelect() {
  const project = state.projects.find(p => p.id === state.project);
  const baseline = project && project.baselineBranch ? project.baselineBranch : "";
  const branches = state.branches || [];
  const options = [`<option value="">不对比</option>`].concat(branches.map(b => `<option value="${b}">${b}</option>`));
  $("baseBranchSelect").innerHTML = options.join("");
  const routedBase = state.pendingRoute && state.pendingRoute.base && branches.includes(state.pendingRoute.base) ? state.pendingRoute.base : "";
  if (routedBase) state.baseBranch = routedBase;
  let next = state.baseBranch;
  if (!next || !branches.includes(next)) {
    next = baseline && branches.includes(baseline) && baseline !== state.branch ? baseline : "";
  }
  if (next === state.branch) next = "";
  state.baseBranch = next;
  $("baseBranchSelect").value = next;
}

async function loadServices() {
  try {
    state.services = await api(`/api/projects/${state.project}/branches/services?${servicesQuery()}`);
  } catch (e) {
    state.services = [];
    $("serviceCount").textContent = "0";
    $("serviceList").innerHTML = `<div class="empty-list">暂无快照，请先刷新当前分支。</div>`;
    updateChangeFilterButtons();
    return;
  }
  renderServiceList();
  if (state.pendingRoute && state.pendingRoute.method) {
    const methodId = state.pendingRoute.method;
    state.pendingRoute = null;
    if (methodExists(methodId)) {
      await selectMethod(methodId, state.branch);
    } else {
      clearSelectedMethod("当前分支不存在该接口方法", "请确认分享链接里的分支和接口是否仍然存在。");
      updateShareUrl();
    }
  }
}

function renderServiceList() {
  updateChangeFilterButtons();
  const services = filteredServices();
  $("serviceCount").textContent = String(services.length);
  if (!services.length) {
    const copy = state.services.length
      ? `当前分支没有${changeFilterLabel(state.serviceFilter)}接口方法。`
      : "暂无快照，请先刷新当前分支。";
    $("serviceList").innerHTML = `<div class="empty-list">${escapeHtml(copy)}</div>`;
    return;
  }
  $("serviceList").innerHTML = services.map(s => `
    <div class="service-item${changeKindClass(s.changeKind)}">
      <div class="service-header">
        <div class="service-name">${escapeHtml(s.fqn)}${changeBadge(s.changeKind, "facade")}</div>
        <span class="badge ${escapeAttr(s.status)}">${statusLabel(s.status)}</span>
      </div>
      ${(s.methods || []).map(m => renderMethodRow(s, m)).join("")}
    </div>`).join("");
  document.querySelectorAll(".method").forEach(el => {
    if (el.dataset.removed === "1") return;
    el.onclick = () => selectMethod(el.dataset.method);
    el.classList.toggle("active", el.dataset.method === selectedMethodId());
  });
}

function filteredServices() {
  if (state.serviceFilter === "all") return state.services;
  const out = [];
  for (const service of state.services) {
    const methods = (service.methods || []).filter(method => methodMatchesFilter(service, method, state.serviceFilter));
    if (methods.length || serviceOnlyMatchesFilter(service, state.serviceFilter)) {
      out.push(Object.assign({}, service, { methods: methods.length ? methods : (service.methods || []) }));
    }
  }
  return out;
}

function methodMatchesFilter(service, method, filter) {
  if (filter === "changed") {
    return changeMatches(service.changeKind, filter)
      || changeMatches(method.changeKind, filter)
      || methodContainsChange(method, filter);
  }
  if (filter === "added") {
    return service.changeKind === "added" || method.changeKind === "added" || methodContainsChange(method, filter);
  }
  if (filter === "removed") {
    return service.changeKind === "removed" || method.changeKind === "removed" || methodContainsChange(method, filter);
  }
  if (filter === "modified") {
    return methodContainsChange(method, filter) || methodOwnModified(method);
  }
  return false;
}

function serviceOnlyMatchesFilter(service, filter) {
  if (filter === "changed") return changeMatches(service.changeKind, filter);
  if (filter === "added") return service.changeKind === "added";
  if (filter === "removed") return service.changeKind === "removed";
  return false;
}

function methodOwnModified(method) {
  if (!method || method.changeKind !== "modified") return false;
  if (methodContainsChange(method, "modified")) return true;
  return !methodContainsChange(method, "added") && !methodContainsChange(method, "removed");
}

function methodContainsChange(method, filter) {
  const params = method && method.params ? method.params : [];
  for (const param of params) {
    if (nodeContainsChange(param.tree, filter)) return true;
  }
  return nodeContainsChange(method && method.returnTree, filter);
}

function nodeContainsChange(node, filter) {
  if (!node) return false;
  if (changeMatches(node.changeKind, filter)) return true;
  return (node.children || []).some(child => nodeContainsChange(child, filter));
}

function changeMatches(kind, filter) {
  if (filter === "all") return true;
  if (filter === "changed") return !!kind && kind !== "unchanged";
  return kind === filter;
}

function changeFilterLabel(filter) {
  return {
    changed: "存在差异的",
    added: "包含新增内容的",
    modified: "包含修改内容的",
    removed: "包含删除内容的"
  }[filter] || "";
}

function updateChangeFilterButtons() {
  document.querySelectorAll("[data-change-filter]").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.changeFilter === state.serviceFilter);
  });
}

function renderMethodRow(service, method) {
  const removed = method.changeKind === "removed" || service.changeKind === "removed";
  const cls = `method${changeKindClass(method.changeKind)}`;
  const dataRemoved = removed ? `data-removed="1"` : "";
  return `<div class="${cls}" data-method="${escapeAttr(method.id)}" ${dataRemoved}><span>${escapeHtml(method.name)}${changeBadge(method.changeKind, "method")}</span></div>`;
}

function changeKindClass(kind) {
  return kind && kind !== "unchanged" ? ` kind-${kind}` : "";
}

function changeBadge(kind, scope = "field") {
  if (!kind || kind === "unchanged") return "";
  const labels = {
    facade: { added: "新增Facade", modified: "Facade差异", removed: "删除Facade" },
    method: { added: "新增接口", modified: "接口差异", removed: "删除接口" },
    field: { added: "新增", modified: "修改", removed: "删除" }
  };
  const title = {
    facade: {
      added: "该 Facade 为当前分支新增",
      modified: "该 Facade 下存在接口方法或报文字段差异",
      removed: "该 Facade 在当前分支已删除"
    },
    method: {
      added: "该接口方法为当前分支新增",
      modified: "该接口方法的签名、入参或出参存在差异",
      removed: "该接口方法在当前分支已删除"
    },
    field: {
      added: "该字段为当前分支新增",
      modified: "该字段的类型、名称、必填、约束或说明存在变更",
      removed: "该字段在当前分支已删除"
    }
  };
  const group = labels[scope] || labels.field;
  const titleGroup = title[scope] || title.field;
  const label = group[kind] || kind;
  return ` <span class="change-badge ${escapeAttr(kind)}" title="${escapeAttr(titleGroup[kind] || label)}">${escapeHtml(label)}</span>`;
}

async function selectMethod(methodId, branch = state.branch, highlightTerm = "") {
  state.branch = branch;
  $("branchSelect").value = branch;
  const qs = servicesQuery();
  const data = await api(`/api/projects/${state.project}/methods/${methodId}?${qs}`);
  state.selected = data;
  state.markdown = await api(`/api/projects/${state.project}/methods/${methodId}/markdown?${qs}`);
  $("methodTitle").textContent = data.method.name;
  $("methodBreadcrumb").textContent = `${state.project} / ${state.branch} / ${data.service.fqn}`;
  $("methodMeta").innerHTML = `
    <div class="meta-chips">
      <span class="meta-chip">${escapeHtml(statusLabel(data.service.status || "candidate"))}</span>
      <span class="meta-chip">commit ${escapeHtml(shortCommit(data.snapshot.commit))}</span>
      ${state.baseBranch && state.baseBranch !== state.branch ? `<span class="meta-chip compare-chip">对比 ${escapeHtml(state.baseBranch)}</span>` : ""}
      ${changeBadge(data.method.changeKind, "method")}
      <span class="meta-chip">${escapeHtml((data.method.params || []).length)} 个入参</span>
      <span class="meta-chip">${escapeHtml(data.method.returnType || "void")}</span>
    </div>`;
  $("docPane").innerHTML = renderMarkdown(state.markdown, data.method);
  $("fieldTree").innerHTML = renderFieldTrees(data.method);
  applySearchHighlight(highlightTerm);
  const exampleJson = JSON.stringify(data.method.requestExample, null, 2);
  renderJsonViewer($("jsonExample"), exampleJson);
  setArgsEditor(exampleJson);
  renderPublishOptions(data.service.publishRecords || [], data.runtime || {});
  setProbeResult("");
  state.selectedCase = null;
  document.querySelectorAll(".method").forEach(el => el.classList.toggle("active", el.dataset.method === methodId));
  updateShareUrl();
  await loadCases();
}

function renderPublishOptions(records, runtime = {}) {
  const select = $("publishSelect");
  const options = [`<option value="">分支默认配置</option>`].concat(records.map((r, index) => {
    const parts = [r.source || "publish", r.binding || "binding", r.uniqueId ? `uniqueId=${r.uniqueId}` : "", r.version ? `version=${r.version}` : ""].filter(Boolean);
    return `<option value="${index}">${escapeHtml(parts.join(" / "))}</option>`;
  }));
  select.innerHTML = options.join("");
  state.publishIndex = records.length ? "0" : "";
  select.value = state.publishIndex;
  renderTargetBox(records, runtime);
}

function renderTargetBox(records = [], runtime = state.selected && state.selected.runtime ? state.selected.runtime : {}) {
  const target = Object.assign({}, runtime || {});
  const selected = state.publishIndex === "" ? null : records[Number(state.publishIndex)];
  if (selected) {
    if (selected.uniqueId) target.uniqueId = selected.uniqueId;
    if (selected.version) target.version = selected.version;
  }
  const parts = [`目标：${target.directUrl || "-"}`];
  if (target.uniqueId) parts.push(`uniqueId=${target.uniqueId}`);
  if (target.version) parts.push(`version=${target.version}`);
  if (target.targetAppName) parts.push(`app=${target.targetAppName}`);
  if (selected && selected.binding) parts.push(`binding=${selected.binding}`);
  $("targetBox").textContent = parts.join(" / ");
}

function clearScanSummary() {
  $("scanSummary").classList.add("hidden");
  $("scanSummary").innerHTML = "";
}

function selectedMethodId() {
  return state.selected && state.selected.method ? state.selected.method.id : "";
}

function methodExists(methodId) {
  if (!methodId) return false;
  return state.services.some(s => (s.methods || []).some(m => m.id === methodId && m.changeKind !== "removed" && s.changeKind !== "removed"));
}

async function refreshSelectedMethod(methodId, missingMessage) {
  if (!methodId) {
    clearSelectedMethod();
    updateShareUrl();
    return;
  }
  if (methodExists(methodId)) {
    await selectMethod(methodId, state.branch);
    return;
  }
  clearSelectedMethod(missingMessage || "当前分支不存在该接口方法", "请选择左侧当前分支下的其他方法。");
  updateShareUrl();
}

function clearSelectedMethod(title = "请选择接口方法", copy = "文档、字段树、JSON 示例和 Diff 会在这里联动展示。") {
  state.selected = null;
  state.markdown = "";
  state.selectedCase = null;
  state.publishIndex = "";
  $("methodBreadcrumb").textContent = state.project && state.branch ? `${state.project} / ${state.branch}` : "项目 / 分支 / 服务 / 方法";
  $("methodTitle").textContent = title;
  $("methodMeta").innerHTML = "";
  $("docPane").innerHTML = `
    <div class="empty-state">
      <div class="empty-title">${escapeHtml(title === "请选择接口方法" ? "选择左侧方法后查看接口文档" : title)}</div>
      <div class="empty-copy">${escapeHtml(copy)}</div>
    </div>`;
  $("fieldTree").innerHTML = "";
  renderJsonViewer($("jsonExample"), "");
  $("diffList").innerHTML = "";
  setArgsEditor("");
  $("publishSelect").innerHTML = "";
  $("targetBox").textContent = "目标：-";
  $("responseBox").textContent = "";
  $("caseList").innerHTML = "";
  setProbeResult("");
  document.querySelectorAll(".method").forEach(el => el.classList.remove("active"));
}

function renderScanSummary(results, title = "扫描结果") {
  const entries = Object.entries(results || {});
  if (!entries.length) {
    clearScanSummary();
    return;
  }
  const counts = entries.reduce((acc, [, r]) => {
    const key = r.status || "unknown";
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});
  const totalText = Object.keys(counts).map(k => `${statusLabel(k)} ${counts[k]}`).join(" / ");
  $("scanSummary").classList.remove("hidden");
  $("scanSummary").innerHTML = `
    <div class="scan-summary-title">${escapeHtml(title)}<span>${escapeHtml(totalText)}</span></div>
    ${entries.map(([branch, r]) => `
      <div class="scan-row">
        <span class="scan-branch">${escapeHtml(branch)}</span>
        <span class="scan-status ${escapeAttr(r.status || "unknown")}">${escapeHtml(statusLabel(r.status || "unknown"))}</span>
        <span class="scan-message">${escapeHtml(r.message || "")}</span>
      </div>`).join("")}`;
}

async function loadScanReports() {
  if (!state.project || !state.branch) return;
  try {
    const reports = await api(`/api/projects/${state.project}/scan-reports?${branchQuery({ limit: 6 })}`);
    renderScanReports(reports);
  } catch (e) {
    renderScanReports([{ status: "failed", message: errorMessage(e), branch: state.branch }]);
  }
}

function renderScanReports(reports) {
  const items = reports || [];
  const box = $("scanReports");
  if (!items.length) {
    box.classList.add("hidden");
    box.innerHTML = "";
    return;
  }
  box.classList.remove("hidden");
  box.innerHTML = `
    <div class="scan-reports-title">最近扫描</div>
    ${items.map(r => `
      <div class="scan-report-row">
        <span class="scan-status ${escapeAttr(r.status || "unknown")}">${escapeHtml(statusLabel(r.status || "unknown"))}</span>
        <span class="scan-time">${escapeHtml(formatTime(r.createdAt))}</span>
        <span class="scan-message">${escapeHtml(r.message || "")}</span>
      </div>`).join("")}`;
}

function formatTime(value) {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString();
}

function renderFieldTrees(method) {
  if (!method) return `<div class="meta">请选择接口方法。</div>`;
  const groups = [];
  (method.params || []).forEach(param => {
    groups.push(renderFieldGroup(`入参 ${param.name || ""}`, param.tree || paramToNode(param)));
  });
  if (method.returnTree) {
    groups.push(renderFieldGroup("出参 return", method.returnTree));
  } else if (method.returnType) {
    groups.push(`<section class="field-group"><h2>出参 return</h2><div class="field-empty"><code>${escapeHtml(method.returnType)}</code></div></section>`);
  }
  return groups.join("") || `<div class="meta">当前方法没有入参或出参字段。</div>`;
}

function paramToNode(param) {
  return {
    path: param.name,
    name: param.name,
    javaType: param.javaType,
    jsonType: param.jsonType,
    comment: param.comment,
    required: param.required,
    enumValues: [],
    children: []
  };
}

function renderFieldGroup(title, node) {
  return `<section class="field-group"><h2>${escapeHtml(title)}</h2>${renderFieldNode(node, 0)}</section>`;
}

function renderFieldNode(node, depth) {
  if (!node) return "";
  const children = node.children || [];
  const enums = node.enumValues || [];
  const open = depth < 1 ? " open" : "";
  const leaf = children.length ? "" : " leaf";
  const kindCls = changeKindClass(node.changeKind);
  const comment = node.comment || node.note || "未填写";
  const required = node.required || "未知";
  const enumHtml = enums.length ? `<div class="field-enums">${enums.map(ev => `<span><code>${escapeHtml(ev.name || "")}</code>${ev.comment ? ` ${escapeHtml(ev.comment)}` : ""}</span>`).join("")}</div>` : "";
  const meta = fieldMeta(node);
  const childrenHtml = children.length ? `<div class="field-children">${children.map(child => renderFieldNode(child, depth + 1)).join("")}</div>` : "";
  return `
    <details class="field-node${leaf}${kindCls}"${open}>
      <summary>
        <span class="field-name">${escapeHtml(node.path || node.name || "-")}</span>
        <code>${escapeHtml(node.javaType || "-")}</code>
        <span class="field-chip">${escapeHtml(node.jsonType || "unknown")}</span>
        <span class="field-required">${escapeHtml(required)}</span>
        ${changeBadge(node.changeKind, "field")}
      </summary>
      <div class="field-comment">${escapeHtml(comment)}${meta}</div>
      ${enumHtml}
      ${childrenHtml}
    </details>`;
}

function fieldMeta(node) {
  const parts = [];
  if (node.jsonName && node.jsonName !== node.name) parts.push(`Jackson 名称：${node.jsonName}`);
  if (node.constraints && node.constraints.length) parts.push(`约束：${node.constraints.join(", ")}`);
  return parts.length ? `<div class="field-meta">${parts.map(escapeHtml).join("<br>")}</div>` : "";
}

function updateShareUrl() {
  const qs = new URLSearchParams({
    project: state.project,
    branch: state.branch
  });
  if (state.selected) {
    qs.set("service", state.selected.service.fqn);
    qs.set("method", state.selected.method.id);
  }
  if (state.baseBranch && state.baseBranch !== state.branch) qs.set("base", state.baseBranch);
  history.replaceState(null, "", "?" + qs.toString());
}

function renderMarkdown(md, method = null) {
  const lines = md.split(/\r?\n/);
  const html = [];
  const changeMap = markdownChangeMap(method);
  for (let i = 0; i < lines.length;) {
    const line = lines[i];
    if (!line.trim()) {
      i++;
      continue;
    }
    if (line.startsWith("```")) {
      const code = [];
      i++;
      while (i < lines.length && !lines[i].startsWith("```")) code.push(lines[i++]);
      i++;
      html.push(`<pre>${escapeHtml(code.join("\n"))}</pre>`);
      continue;
    }
    if (line.startsWith("### ")) {
      html.push(`<h3>${inlineMarkdown(line.slice(4))}</h3>`);
      i++;
      continue;
    }
    if (line.startsWith("## ")) {
      html.push(`<h2>${inlineMarkdown(line.slice(3))}</h2>`);
      i++;
      continue;
    }
    if (line.startsWith("# ")) {
      html.push(`<h1>${inlineMarkdown(line.slice(2))}</h1>`);
      i++;
      continue;
    }
    if (isTableStart(lines, i)) {
      const headers = tableCells(lines[i]);
      const fieldPathCol = headers.findIndex(h => plainMarkdownCell(h) === "字段路径");
      const changeCol = headers.findIndex(h => plainMarkdownCell(h) === "变更");
      const isFieldTable = fieldPathCol >= 0;
      i += 2;
      const rows = [];
      while (i < lines.length && lines[i].trim().startsWith("|")) rows.push(tableCells(lines[i++]));
      html.push(`<table${isFieldTable ? ` class="doc-field-table"` : ""}><thead><tr>${headers.map(c => `<th>${inlineMarkdown(c)}</th>`).join("")}</tr></thead><tbody>${rows.map(row => {
        const path = fieldPathCol >= 0 ? plainMarkdownCell(row[fieldPathCol]) : "";
        const rowKind = changeCol >= 0 ? changeKindFromLabel(row[changeCol]) : changeMap[path];
        const rowClass = rowKind && rowKind !== "unchanged" ? ` class="doc-change-row${changeKindClass(rowKind)}"` : "";
        return `<tr${rowClass}>${row.map((c, col) => {
          if (col === changeCol) return `<td>${changeBadge(rowKind, "field") || `<span class="doc-no-change">-</span>`}</td>`;
          return `<td>${inlineMarkdown(c)}</td>`;
        }).join("")}</tr>`;
      }).join("")}</tbody></table>`);
      continue;
    }
    if (line.startsWith("- ")) {
      const items = [];
      while (i < lines.length && lines[i].startsWith("- ")) items.push(lines[i++].slice(2));
      html.push(`<ul>${items.map(item => `<li>${inlineMarkdown(item)}</li>`).join("")}</ul>`);
      continue;
    }
    if (line.startsWith("> ")) {
      html.push(`<blockquote>${inlineMarkdown(line.slice(2))}</blockquote>`);
      i++;
      continue;
    }
    html.push(`<p>${inlineMarkdown(line)}</p>`);
    i++;
  }
  return html.join("");
}

function markdownChangeMap(method) {
  const out = {};
  const walk = (node) => {
    if (!node) return;
    if (node.path && node.changeKind) out[node.path] = node.changeKind;
    (node.children || []).forEach(walk);
  };
  (method && method.params ? method.params : []).forEach(param => walk(param.tree));
  if (method && method.returnTree) walk(method.returnTree);
  return out;
}

function plainMarkdownCell(cell) {
  return String(cell == null ? "" : cell)
    .replace(/`/g, "")
    .replace(/<br>/g, " ")
    .replace(/&lt;br&gt;/g, " ")
    .trim();
}

function changeKindFromLabel(label) {
  const value = plainMarkdownCell(label);
  return { 新增: "added", 修改: "modified", 删除: "removed", 已删除: "removed" }[value] || "";
}

function isTableStart(lines, i) {
  return lines[i] && lines[i].trim().startsWith("|") && lines[i + 1] && /^\s*\|?\s*-{3,}/.test(lines[i + 1]);
}

function tableCells(line) {
  return line.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map(cell => cell.trim());
}

function inlineMarkdown(s) {
  return escapeHtml(s)
    .replace(/&lt;br&gt;/g, "<br>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
}

function escapeHtml(s) {
  return String(s == null ? "" : s).replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
}

function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, "&quot;");
}

function statusLabel(status) {
  return {
    published: "已发布",
    candidate: "候选",
    source_missing: "源码缺失",
    incomplete: "配置不完整",
    success: "成功",
    unchanged: "未变化",
    skipped: "跳过",
    failed: "失败"
  }[status] || status || "未知";
}

function shortCommit(commit) {
  return commit ? String(commit).slice(0, 8) : "-";
}

function renderSearchSnippet(snippet) {
  return escapeHtml(snippet || "")
    .replace(/&lt;mark&gt;/g, "<mark>")
    .replace(/&lt;\/mark&gt;/g, "</mark>");
}

function applySearchHighlight(term) {
  clearHighlights($("docPane"));
  clearHighlights($("fieldTree"));
  const q = (term || "").trim();
  if (!q) return;
  highlightElement($("docPane"), q);
  highlightElement($("fieldTree"), q);
  document.querySelectorAll("#fieldTree mark").forEach(mark => {
    let node = mark.parentElement;
    while (node) {
      if (node.tagName === "DETAILS") node.open = true;
      node = node.parentElement;
    }
  });
}

function clearHighlights(root) {
  if (!root) return;
  root.querySelectorAll("mark.search-hit").forEach(mark => {
    mark.replaceWith(document.createTextNode(mark.textContent));
  });
  root.normalize();
}

function highlightElement(root, term) {
  if (!root) return;
  const pattern = new RegExp(escapeRegExp(term), "gi");
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (!node.nodeValue || !pattern.test(node.nodeValue)) return NodeFilter.FILTER_REJECT;
      pattern.lastIndex = 0;
      return NodeFilter.FILTER_ACCEPT;
    }
  });
  const nodes = [];
  while (walker.nextNode()) nodes.push(walker.currentNode);
  nodes.forEach(node => {
    const frag = document.createDocumentFragment();
    const text = node.nodeValue;
    let last = 0;
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(text)) !== null) {
      frag.appendChild(document.createTextNode(text.slice(last, match.index)));
      const mark = document.createElement("mark");
      mark.className = "search-hit";
      mark.textContent = match[0];
      frag.appendChild(mark);
      last = match.index + match[0].length;
    }
    frag.appendChild(document.createTextNode(text.slice(last)));
    node.replaceWith(frag);
  });
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function highlightJsonText(text) {
  let html = escapeHtml(text);
  html = html.replace(/("(?:\\.|[^"\\])*")(\s*:)?/g, (m, str, colon) => colon
    ? `<span class="j-key">${str}</span><span class="j-punct">${colon}</span>`
    : `<span class="j-str">${str}</span>`);
  html = html.replace(/\b(true|false|null)\b/g, '<span class="j-lit">$1</span>');
  html = html.replace(/([^\w"]|^)(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)(?!\w)/g, '$1<span class="j-num">$2</span>');
  return html;
}

function buildJsonFolds(lines) {
  const stack = [];
  const folds = {};
  lines.forEach((line, idx) => {
    if (/[\[{]\s*,?\s*$/.test(line)) stack.push(idx);
    const closes = (line.match(/^\s*[\]}]/g) || []).length;
    for (let k = 0; k < closes; k++) {
      const open = stack.pop();
      if (open != null && idx > open + 1) folds[open] = idx;
    }
  });
  return folds;
}

function renderJsonViewer(root, text) {
  if (!root || typeof root.querySelector !== "function") return;
  const lines = String(text == null ? "" : text).split("\n");
  const folds = buildJsonFolds(lines);
  if (!root._foldState) root._foldState = {};
  const state = root._foldState;
  Object.keys(state).forEach(k => { if (folds[k] == null) delete state[k]; });
  const gutter = root.querySelector(".json-gutter");
  const body = root.querySelector(".json-body");
  if (!gutter || !body) return;
  paint();
  if (!root._wired) {
    gutter.addEventListener("click", (e) => {
      const t = e.target.closest(".ln");
      if (!t) return;
      const i = Number(t.dataset.ln);
      if (folds[i] == null) return;
      state[i] = !state[i];
      paint();
    });
    body.addEventListener("click", (e) => {
      const t = e.target.closest(".fold-summary");
      if (!t) return;
      const i = Number(t.dataset.ln);
      if (folds[i] == null) return;
      state[i] = false;
      paint();
    });
    root._wired = true;
  }
  function isHidden(i) {
    for (const open in folds) {
      const o = Number(open);
      const c = folds[o];
      if (state[o] && i > o && i <= c) return true;
    }
    return false;
  }
  function paint() {
    gutter.innerHTML = lines.map((_, i) => {
      const isFold = folds[i] != null;
      const hidden = isHidden(i);
      const cls = (isFold ? (state[i] ? "ln folded" : "ln fold") : "ln") + (hidden ? " hidden-fold" : "");
      return `<span class="${cls}" data-ln="${i}">${i + 1}</span>`;
    }).join("");
    body.innerHTML = lines.map((line, i) => {
      const hidden = isHidden(i);
      const cls = "json-line" + (hidden ? " hidden-fold" : "");
      let html = highlightJsonLine(line);
      if (state[i] && folds[i] != null) {
        html += ` <span class="fold-summary" data-ln="${i}">⋯ ${folds[i] - i - 1} 行</span>`;
      }
      return `<span class="${cls}" data-ln="${i}">${html || "&nbsp;"}</span>`;
    }).join("");
  }
}

function highlightJsonLine(line) {
  return highlightJsonText(line);
}

function setArgsEditor(text) {
  const ta = $("argsEditor");
  if (!ta) return;
  ta.value = String(text == null ? "" : text);
  paintArgsEditor();
  ta.scrollTop = 0;
  ta.scrollLeft = 0;
  syncArgsEditorScroll();
}

function paintArgsEditor() {
  const wrap = $("argsEditorWrap");
  const ta = $("argsEditor");
  if (!wrap || !ta || typeof wrap.querySelector !== "function") return;
  const text = ta.value;
  const lines = text.split("\n");
  const gutter = wrap.querySelector(".code-gutter");
  const highlight = wrap.querySelector(".code-highlight");
  if (gutter) gutter.textContent = lines.map((_, i) => i + 1).join("\n");
  if (highlight) highlight.innerHTML = highlightJsonText(text) + "\n";
}

function syncArgsEditorScroll() {
  const wrap = $("argsEditorWrap");
  const ta = $("argsEditor");
  if (!wrap || !ta || typeof wrap.querySelector !== "function") return;
  const highlight = wrap.querySelector(".code-highlight");
  const gutter = wrap.querySelector(".code-gutter");
  if (highlight) {
    highlight.scrollTop = ta.scrollTop;
    highlight.scrollLeft = ta.scrollLeft;
  }
  if (gutter) gutter.scrollTop = ta.scrollTop;
}

function currentSchemaPaths() {
  const out = new Set();
  if (!state.selected || !state.selected.method) return out;
  (state.selected.method.params || []).forEach(param => {
    if (param.tree) collectSchemaLeafPaths(param.tree, out);
    else if (param.name) out.add(param.name);
  });
  return out;
}

function collectSchemaLeafPaths(node, out) {
  if (!node) return;
  const children = node.children || [];
  if (!children.length) {
    if (node.path || node.name) out.add(node.path || node.name);
    return;
  }
  children.forEach(child => collectSchemaLeafPaths(child, out));
}

function currentArgsPaths(args) {
  const out = new Set();
  if (!state.selected || !state.selected.method) return out;
  const params = state.selected.method.params || [];
  if (params.length === 1) {
    collectValueLeafPaths(params[0].name || "arg0", args, out);
    return out;
  }
  if (!Array.isArray(args)) return out;
  params.forEach((param, index) => collectValueLeafPaths(param.name || `arg${index}`, args[index], out));
  return out;
}

function collectValueLeafPaths(path, value, out) {
  if (Array.isArray(value)) {
    if (!value.length) out.add(path);
    value.forEach(item => collectValueLeafPaths(path + "[]", item, out));
    return;
  }
  if (value && typeof value === "object") {
    const keys = Object.keys(value);
    if (!keys.length) out.add(path);
    keys.forEach(key => collectValueLeafPaths(path + "." + key, value[key], out));
    return;
  }
  out.add(path);
}

function summarizeCaseDrift(args, validation) {
  const schema = currentSchemaPaths();
  const actual = currentArgsPaths(args);
  const missing = [...schema].filter(path => !actual.has(path));
  const stale = [...actual].filter(path => !schema.has(path));
  const errors = validation && validation.errors ? validation.errors : [];
  const warnings = validation && validation.warnings ? validation.warnings : [];
  const typeOrEnum = errors.filter(item => /must be one of|must be object|must be array|must be boolean|must be number|must be string/.test(item));
  const required = warnings.filter(item => item.endsWith(" is required"));
  const unknown = warnings.filter(item => item.endsWith(" is unknown"));
  const parts = [];
  if (missing.length) parts.push("当前接口字段未出现在用例中：" + conciseList(missing));
  if (stale.length || unknown.length) parts.push("用例包含当前接口不存在字段：" + conciseList(stale.concat(unknown.map(item => item.replace(/ is unknown$/, "")))));
  if (typeOrEnum.length) parts.push("类型/枚举不匹配：" + conciseList(typeOrEnum));
  if (required.length) parts.push("必填缺失：" + conciseList(required.map(item => item.replace(/ is required$/, ""))));
  return parts;
}

function conciseList(items) {
  const unique = [...new Set(items)].filter(Boolean);
  const head = unique.slice(0, 5).join("、");
  return unique.length > 5 ? `${head} 等 ${unique.length} 项` : head;
}

async function loadCases() {
  if (!state.selected) return;
  const qs = branchQuery({ service: state.selected.service.fqn });
  const cases = await api(`/api/projects/${state.project}/methods/${state.selected.method.id}/cases?${qs}`);
  $("caseList").innerHTML = cases.map(c => `
    <div class="case" data-id="${c.id}" data-json="${encodeURIComponent(c.argsJson)}" data-name="${escapeAttr(c.name)}">
      <div class="case-row">
        <span>${escapeHtml(c.name)}</span>
        <button class="case-delete" data-id="${c.id}">删除</button>
      </div>
      <div class="meta">${c.updatedAt || ""}</div>
    </div>`).join("");
  document.querySelectorAll(".case").forEach(el => el.onclick = async (event) => {
    if (event.target.classList.contains("case-delete")) return;
    state.selectedCase = { id: Number(el.dataset.id), name: el.dataset.name };
    setArgsEditor(decodeURIComponent(el.dataset.json));
    await validateCurrentArgs("用例已载入", true);
  });
  document.querySelectorAll(".case-delete").forEach(el => el.onclick = async () => {
    await api(`/api/projects/${state.project}/cases/${el.dataset.id}`, { method: "DELETE" });
    if (state.selectedCase && state.selectedCase.id === Number(el.dataset.id)) state.selectedCase = null;
    await loadCases();
  });
}

const configWorkbench = createConfigWorkbench({ state, $, api, init, setConfigResult, errorMessage, escapeHtml, escapeAttr });

$("tokenSave").onclick = async () => {
  token = $("tokenInput").value.trim();
  sessionStorage.setItem("sofaDocToken", token);
  await init();
};

$("projectSelect").onchange = async () => {
  state.project = $("projectSelect").value;
  state.branch = "";
  state.baseBranch = "";
  state.pendingRoute = null;
  await loadBranches();
  clearSelectedMethod();
  updateShareUrl();
};
$("branchSelect").onchange = async () => {
  const previousMethodId = selectedMethodId();
  state.branch = $("branchSelect").value;
  populateBaseBranchSelect();
  clearScanSummary();
  await loadServices();
  await refreshSelectedMethod(previousMethodId, "当前分支不存在该接口方法");
  await loadScanReports();
};
$("baseBranchSelect").onchange = async () => {
  state.baseBranch = $("baseBranchSelect").value;
  const previousMethodId = selectedMethodId();
  await loadServices();
  await refreshSelectedMethod(previousMethodId, "当前分支不存在该接口方法");
};
$("publishSelect").onchange = () => {
  state.publishIndex = $("publishSelect").value;
  renderTargetBox(state.selected && state.selected.service ? state.selected.service.publishRecords || [] : []);
};
$("scanBtn").onclick = async () => {
  const original = $("scanBtn").textContent;
  $("scanBtn").textContent = "扫描中...";
  $("scanBtn").disabled = true;
  try {
    const result = await api(`/api/projects/${state.project}/branches/scan?${branchQuery()}`, { method: "POST" });
    renderScanSummary({ [state.branch]: result }, "当前分支");
    const previousMethodId = selectedMethodId();
    await loadServices();
    await refreshSelectedMethod(previousMethodId, "刷新后当前分支不存在该接口方法");
  } catch (e) {
    renderScanSummary({ [state.branch]: { status: "failed", message: errorMessage(e) } }, "当前分支");
  } finally {
    await loadScanReports();
    $("scanBtn").textContent = original;
    $("scanBtn").disabled = false;
  }
};
$("scanAllBtn").onclick = async () => {
  const original = $("scanAllBtn").textContent;
  $("scanAllBtn").textContent = "扫描中...";
  $("scanAllBtn").disabled = true;
  $("scanBtn").disabled = true;
  try {
    const results = await api(`/api/projects/${state.project}/scan`, { method: "POST" });
    const previousMethodId = selectedMethodId();
    await loadBranches();
    await refreshSelectedMethod(previousMethodId, "刷新后当前分支不存在该接口方法");
    renderScanSummary(results, "全部分支");
  } catch (e) {
    renderScanSummary({ [state.project]: { status: "failed", message: errorMessage(e) } }, "全部分支");
  } finally {
    await loadScanReports();
    $("scanAllBtn").textContent = original;
    $("scanAllBtn").disabled = false;
    $("scanBtn").disabled = false;
  }
};
$("copyLink").onclick = () => navigator.clipboard.writeText(location.href);
$("configBtn").onclick = openConfigModal;
$("configClose").onclick = closeConfigModal;
$("configReload").onclick = openConfigModal;
$("configSave").onclick = saveConfig;
$("configAddProject").onclick = () => configWorkbench.addProject();
$("configBackdrop").onclick = closeConfigModal;
$("probeBtn").onclick = async () => {
  if (!state.selected) return;
  try {
    const r = await api(`/api/projects/${state.project}/methods/${state.selected.method.id}/probe?${branchQuery()}`);
    setProbeResult(r.reachable ? `可连接，${r.latencyMs}ms` : `不可连接：${r.error}`, !r.reachable);
  } catch (e) {
    setProbeResult(`连通性检查失败：${errorMessage(e)}`, true);
  }
};
$("invokeBtn").onclick = async () => {
  if (!state.selected) return;
  let body;
  try {
    body = { args: JSON.parse($("argsEditor").value) };
  } catch (e) {
    $("responseBox").textContent = "请求 JSON 不合法：" + e.message;
    return;
  }
  try {
    const r = await api(`/api/projects/${state.project}/methods/${state.selected.method.id}/invoke?${invokeQuery()}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    $("responseBox").textContent = renderInvokeResult(r);
    if (r.status === "unreachable") setProbeResult(r.error || "目标服务不可连接", true);
  } catch (e) {
    $("responseBox").textContent = errorMessage(e);
  }
};
async function validateCurrentArgs(prefix = "校验", includeDrift = false) {
  if (!state.selected) return;
  let body;
  try {
    body = { args: JSON.parse($("argsEditor").value) };
  } catch (e) {
    setProbeResult(`${prefix}：JSON 不合法`, true);
    return;
  }
  try {
    const r = await api(`/api/projects/${state.project}/methods/${state.selected.method.id}/validate?${branchQuery()}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const warnings = r.warnings && r.warnings.length ? `；警告：${r.warnings.join("；")}` : "";
    const drift = includeDrift ? summarizeCaseDrift(body.args, r) : [];
    const driftText = drift.length ? `；接口漂移：${drift.join("；")}` : includeDrift ? "；接口漂移：未发现明显字段漂移" : "";
    setProbeResult(r.ok ? `${prefix}：结构匹配当前接口${warnings}${driftText}` : `${prefix}：${r.errors.join("；")}${warnings}${driftText}`, !r.ok || drift.length > 0);
  } catch (e) {
    setProbeResult(`${prefix}失败：${errorMessage(e)}`, true);
  }
}
$("saveCaseBtn").onclick = async () => {
  if (!state.selected) return;
  const name = prompt("用例名称");
  if (!name) return;
  const qs = branchQuery({ service: state.selected.service.fqn });
  try {
    await api(`/api/projects/${state.project}/methods/${state.selected.method.id}/cases?${qs}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, argsJson: $("argsEditor").value })
    });
    setProbeResult("用例已保存");
    await loadCases();
  } catch (e) {
    setProbeResult(`保存用例失败：${errorMessage(e)}`, true);
  }
};
$("updateCaseBtn").onclick = async () => {
  if (!state.selected || !state.selectedCase) return;
  const payload = {
    branch: state.branch,
    service: state.selected.service.fqn,
    methodId: state.selected.method.id,
    name: state.selectedCase.name,
    argsJson: $("argsEditor").value
  };
  try {
    await api(`/api/projects/${state.project}/cases/${state.selectedCase.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    setProbeResult("用例已更新");
    await loadCases();
  } catch (e) {
    setProbeResult(`更新用例失败：${errorMessage(e)}`, true);
  }
};
$("copyMd").onclick = () => navigator.clipboard.writeText(state.markdown || "");
$("downloadMd").onclick = () => {
  const blob = new Blob([state.markdown || ""], { type: "text/markdown" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "facade-method.md";
  a.click();
};
$("sidebarToggle").onclick = () => {
  state.sidebarCollapsed = !state.sidebarCollapsed;
  sessionStorage.setItem("sofaSidebarCollapsed", state.sidebarCollapsed ? "1" : "0");
  applySidebarCollapsed();
};
$("invokeToggle").onclick = () => {
  state.invokeCollapsed = !state.invokeCollapsed;
  sessionStorage.setItem("sofaInvokeCollapsed", state.invokeCollapsed ? "1" : "0");
  applyInvokeCollapsed();
};
document.querySelectorAll(".tabs button").forEach(btn => btn.onclick = async () => {
  document.querySelectorAll(".tabs button").forEach(b => b.classList.remove("active"));
  btn.classList.add("active");
  ["doc", "tree", "json", "diff"].forEach(name => $(name + "Pane").classList.toggle("hidden", btn.dataset.tab !== name));
  if (btn.dataset.tab === "diff") {
    const changes = await api(`/api/projects/${state.project}/diff?${servicesQuery()}`);
    const title = state.baseBranch && state.baseBranch !== state.branch
      ? `<div class="diff-title">${escapeHtml(state.branch)} 对比 ${escapeHtml(state.baseBranch)}</div>`
      : `<div class="diff-title">${escapeHtml(state.branch)} 对比默认基线</div>`;
    $("diffList").innerHTML = changes.length
      ? title + changes.map(c => `
        <div class="change ${escapeAttr(c.kind || "")}">
          <div class="change-head"><b>${escapeHtml(c.kind || "")}</b></div>
          <div class="change-path">${escapeHtml(c.path || "")}</div>
          <div class="change-msg">${escapeHtml(c.message || "")}</div>
        </div>`).join("")
      : `${title}<div class="meta">暂无变更</div>`;
  }
});
document.querySelectorAll("[data-change-filter]").forEach(btn => {
  btn.onclick = () => {
    state.serviceFilter = btn.dataset.changeFilter || "all";
    $("search").value = "";
    renderServiceList();
  };
});
$("search").onkeydown = async (e) => {
  if (e.key !== "Enter") return;
  const query = $("search").value.trim();
  if (!query) {
    renderServiceList();
    return;
  }
  try {
    state.serviceFilter = "all";
    updateChangeFilterButtons();
    const hits = await api(`/api/projects/${state.project}/search?q=${encodeURIComponent(query)}`);
    $("serviceCount").textContent = String(hits.length);
    $("serviceList").innerHTML = hits.length ? hits.map(h => `
      <div class="service-item search-result">
        <div class="service-header">
          <div class="service-name">${escapeHtml(h.service)}</div>
          <span class="badge candidate">${escapeHtml(h.branch)}</span>
        </div>
        <div class="method" data-method="${escapeAttr(h.methodId)}" data-branch="${escapeAttr(h.branch)}"><span>${escapeHtml(h.method)}</span></div>
        <div class="meta">${renderSearchSnippet(h.snippet)}</div>
      </div>`).join("") : `<div class="empty-list">没有匹配结果。</div>`;
    document.querySelectorAll(".method").forEach(el => el.onclick = () => selectMethod(el.dataset.method, el.dataset.branch, query));
  } catch (err) {
    $("serviceList").innerHTML = `<div class="empty-list error-text">搜索失败：${escapeHtml(errorMessage(err))}</div>`;
  }
};

$("argsEditor").oninput = paintArgsEditor;
$("argsEditor").onscroll = syncArgsEditorScroll;
paintArgsEditor();

init().catch(err => {
  sessionStorage.removeItem("sofaDocToken");
  console.error(err);
});
