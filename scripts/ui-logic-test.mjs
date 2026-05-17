#!/usr/bin/env node

import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

class ClassList {
  constructor(initial = "") {
    this.values = new Set(initial.split(/\s+/).filter(Boolean));
  }

  add(value) {
    this.values.add(value);
  }

  remove(value) {
    this.values.delete(value);
  }

  contains(value) {
    return this.values.has(value);
  }

  toggle(value, force) {
    const enabled = force === undefined ? !this.values.has(value) : !!force;
    if (enabled) this.values.add(value);
    else this.values.delete(value);
    return enabled;
  }

  toString() {
    return [...this.values].join(" ");
  }
}

class ElementMock {
  constructor(id = "", options = {}) {
    this.id = id;
    this.dataset = Object.assign({}, options.dataset || {});
    this.children = [];
    this.attributes = {};
    this.classList = new ClassList(options.className || "");
    this.value = options.value || "";
    this.textContent = options.textContent || "";
    this.disabled = false;
    this.onclick = null;
    this.onchange = null;
    this.onkeydown = null;
    this._innerHTML = "";
  }

  set innerHTML(value) {
    this._innerHTML = String(value || "");
    if (this.id === "projectSelect" || this.id === "branchSelect" || this.id === "publishSelect") {
      const values = [...this._innerHTML.matchAll(/<option value="([^"]*)"/g)].map(match => decodeEntities(match[1]));
      if (values.length && !values.includes(this.value)) this.value = values[0];
    }
    if (this.id === "serviceList") documentMock.rebuildMethods(this._innerHTML);
    if (this.id === "caseList") documentMock.caseElements = [];
  }

  get innerHTML() {
    return this._innerHTML;
  }

  querySelectorAll() {
    return [];
  }

  setAttribute(name, value) {
    this.attributes[name] = String(value);
    if (name === "title") this.title = String(value);
  }

  normalize() {
  }

  focus() {
  }
}

function decodeEntities(value) {
  return String(value || "").replace(/&quot;/g, "\"").replace(/&amp;/g, "&");
}

function createDocumentMock() {
  const ids = [
    "login", "app", "tokenInput", "tokenSave", "projectSelect", "branchSelect",
    "scanBtn", "scanAllBtn", "scanSummary", "scanReports", "serviceCount",
    "serviceList", "methodBreadcrumb", "methodTitle", "methodMeta", "docPane",
    "treePane", "fieldTree", "jsonPane", "jsonExample", "diffPane", "diffList",
    "publishSelect", "targetBox", "probeBtn", "probeResult", "argsEditor",
    "invokeBtn", "saveCaseBtn", "updateCaseBtn", "responseBox", "caseList",
    "copyMd", "downloadMd", "copyLink", "sidebarToggle", "invokeToggle", "configBtn", "configModal",
    "configBackdrop", "configPath", "configEditor", "configResult",
    "configClose", "configReload", "configSave", "search"
  ];
  const elements = new Map(ids.map(id => [id, new ElementMock(id)]));
  elements.get("login").classList.add("login");
  elements.get("app").classList.add("hidden");
  elements.get("scanSummary").classList.add("hidden");
  elements.get("scanReports").classList.add("hidden");
  elements.get("configModal").classList.add("hidden");
  elements.get("scanBtn").textContent = "刷新当前分支";
  elements.get("scanAllBtn").textContent = "刷新全部分支";
  const tabs = ["doc", "tree", "json", "diff"].map(tab => new ElementMock("", { dataset: { tab }, className: tab === "doc" ? "active" : "" }));
  const changeFilters = ["all", "changed", "added", "modified", "removed"]
    .map(filter => new ElementMock("", { dataset: { changeFilter: filter }, className: filter === "all" ? "active" : "" }));

  return {
    elements,
    methodElements: [],
    caseElements: [],
    tabElements: tabs,
    changeFilterElements: changeFilters,
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, new ElementMock(id));
      return elements.get(id);
    },
    querySelectorAll(selector) {
      if (selector === ".method") return this.methodElements;
      if (selector === ".case") return this.caseElements;
      if (selector === ".case-delete") return [];
      if (selector === ".tabs button") return this.tabElements;
      if (selector === "[data-change-filter]") return this.changeFilterElements;
      if (selector === "#fieldTree mark") return [];
      return [];
    },
    createTextNode(text) {
      return { textContent: String(text || "") };
    },
    createDocumentFragment() {
      return { children: [], appendChild(node) { this.children.push(node); } };
    },
    createElement(tagName) {
      const el = new ElementMock("");
      el.tagName = String(tagName || "").toUpperCase();
      el.click = () => {};
      return el;
    },
    createTreeWalker() {
      return { nextNode: () => false, currentNode: null };
    },
    rebuildMethods(html) {
      this.methodElements = [...html.matchAll(/<div class="method"([^>]*)>/g)].map(match => {
        const attrs = match[1];
        const dataset = {};
        for (const attr of attrs.matchAll(/data-([a-z-]+)="([^"]*)"/g)) {
          const key = attr[1].replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
          dataset[key] = decodeEntities(attr[2]);
        }
        return new ElementMock("", { dataset, className: "method" });
      });
    }
  };
}

function apiData(path) {
  const url = new URL(path, "http://ui.test");
  const branch = url.searchParams.get("branch");
  if (url.pathname === "/api/projects") {
    return [{ id: "loan", displayName: "贷款服务" }];
  }
  if (url.pathname === "/api/projects/loan/branches") {
    return ["main", "feature/apply-flow", "feature/removed"];
  }
  if (url.pathname === "/api/config/projects") {
    return {
      admin: false,
      teams: [{ id: "loan-team", displayName: "贷款团队" }],
      projects: [{
        id: "loan",
        team: "loan-team",
        displayName: "贷款服务",
        repo: "/repo/loan",
        baselineBranch: "main",
        tokens: ["loan-token"],
        branches: { include: ["main", "feature/*"], exclude: [], maxMatched: 20 },
        branchDefaults: { directUrl: "bolt://feature:12201", springProfiles: ["test"] },
        sourceRoots: ["facade/src/main/java"],
        resourceRoots: ["service/src/main/resources"],
        facadePackages: ["com.company.loan.facade"],
        branchOverrides: {}
      }]
    };
  }
  if (url.pathname === "/api/projects/loan/branches/services") {
    if (branch === "feature/removed") return [{
      fqn: "com.company.loan.facade.OtherFacade",
      status: "candidate",
      methods: [{ id: "otherMethod-000000000000", name: "otherMethod" }]
    }];
    return [{
      fqn: "com.company.loan.facade.LoanQueryFacade",
      status: branch === "main" ? "published" : "candidate",
      methods: [{ id: "queryStatus-c0927e9de749", name: "queryStatus" }]
    }];
  }
  if (url.pathname === "/api/projects/loan/scan-reports") {
    return [];
  }
  if (url.pathname === "/api/projects/loan/methods/queryStatus-c0927e9de749") {
    return {
      snapshot: { branch, commit: branch === "main" ? "maincommit1234" : "featurecommit1234" },
      service: {
        fqn: "com.company.loan.facade.LoanQueryFacade",
        status: branch === "main" ? "published" : "candidate",
        publishRecords: []
      },
      runtime: {
        directUrl: branch === "main" ? "bolt://main:12200" : "bolt://feature:12201",
        targetAppName: branch === "main" ? "loan-main" : "loan-feature"
      },
      method: {
        id: "queryStatus-c0927e9de749",
        name: "queryStatus",
        params: [{ name: "request", javaType: "com.company.loan.facade.dto.QueryRequest", jsonType: "object" }],
        returnType: "com.company.loan.facade.dto.ApplyResponse",
        requestExample: { orderNo: "T1" }
      }
    };
  }
  if (url.pathname === "/api/projects/loan/methods/queryStatus-c0927e9de749/markdown") {
    return `# com.company.loan.facade.LoanQueryFacade.queryStatus\n\nbranch=${branch}`;
  }
  if (url.pathname === "/api/projects/loan/methods/queryStatus-c0927e9de749/cases") {
    return [];
  }
  throw new Error(`unexpected API path: ${path}`);
}

function createFetch(calls) {
  return async (path, options = {}) => {
    calls.push({ path, options });
    const body = apiData(path);
    const isText = typeof body === "string";
    return {
      ok: true,
      status: 200,
      headers: { get: () => isText ? "text/markdown;charset=UTF-8" : "application/json" },
      json: async () => body,
      text: async () => isText ? body : JSON.stringify(body)
    };
  };
}

const configWorkbenchPath = new URL("../src/main/resources/static/config-workbench.js", import.meta.url);
const appPath = new URL("../src/main/resources/static/app.js", import.meta.url);
let source = await readFile(configWorkbenchPath, "utf8");
source += "\n" + await readFile(appPath, "utf8");
source = source.replace(/\ninit\(\)\.catch\(err => \{\n  sessionStorage\.removeItem\("sofaDocToken"\);\n  console\.error\(err\);\n\}\);\s*$/, `
globalThis.__app = { state, init, loadBranches, loadServices, selectMethod, refreshSelectedMethod, selectedMethodId, methodExists, clearSelectedMethod, branchQuery, renderServiceList, filteredServices };
globalThis.__initPromise = init().catch(err => {
  sessionStorage.removeItem("sofaDocToken");
  console.error(err);
  throw err;
});
`);

assert.notEqual(source.includes("globalThis.__initPromise"), false, "app.js footer was not replaced");

const documentMock = createDocumentMock();
const fetchCalls = [];
const context = {
  console,
  document: documentMock,
  location: { search: "?project=loan&branch=feature%2Fapply-flow&method=queryStatus-c0927e9de749", href: "http://ui.test/" },
  history: {
    lastUrl: "",
    replaceState(_state, _title, url) {
      this.lastUrl = url;
      context.location.search = url.startsWith("?") ? url : new URL(url, "http://ui.test").search;
    }
  },
  sessionStorage: {
    values: new Map([["sofaDocToken", "admin-token"]]),
    getItem(key) { return this.values.get(key) || ""; },
    setItem(key, value) { this.values.set(key, String(value)); },
    removeItem(key) { this.values.delete(key); }
  },
  navigator: { clipboard: { writeText: async () => {} } },
  fetch: createFetch(fetchCalls),
  URLSearchParams,
  URL,
  Blob,
  NodeFilter: { SHOW_TEXT: 4, FILTER_REJECT: 2, FILTER_ACCEPT: 1 }
};
context.globalThis = context;

vm.createContext(context);
vm.runInContext(source, context, { filename: "app.js" });
await context.__initPromise;

assert.equal(context.__app.state.project, "loan");
assert.equal(context.__app.state.branch, "feature/apply-flow");
assert.equal(documentMock.getElementById("methodTitle").textContent, "queryStatus");
assert.match(documentMock.getElementById("methodBreadcrumb").textContent, /loan \/ feature\/apply-flow/);
assert.match(documentMock.getElementById("targetBox").textContent, /bolt:\/\/feature:12201/);
assert.match(context.history.lastUrl, /branch=feature%2Fapply-flow/);
assert.match(context.history.lastUrl, /method=queryStatus-c0927e9de749/);

await documentMock.getElementById("configBtn").onclick();
assert.equal(documentMock.getElementById("configModal").classList.contains("hidden"), false);
assert.match(documentMock.getElementById("configPath").textContent, /贷款团队/);
assert.match(documentMock.getElementById("configEditor").value, /"projects"/);
documentMock.getElementById("configClose").onclick();

documentMock.getElementById("branchSelect").value = "main";
await documentMock.getElementById("branchSelect").onchange();
assert.equal(context.__app.state.branch, "main");
assert.equal(documentMock.getElementById("methodTitle").textContent, "queryStatus");
assert.match(documentMock.getElementById("methodBreadcrumb").textContent, /loan \/ main/);
assert.match(documentMock.getElementById("targetBox").textContent, /bolt:\/\/main:12200/);
assert.match(context.history.lastUrl, /branch=main/);
assert.match(context.history.lastUrl, /method=queryStatus-c0927e9de749/);

documentMock.getElementById("branchSelect").value = "feature/removed";
await documentMock.getElementById("branchSelect").onchange();
assert.equal(context.__app.state.branch, "feature/removed");
assert.equal(context.__app.state.selected, null);
assert.equal(documentMock.getElementById("methodTitle").textContent, "当前分支不存在该接口方法");
assert.equal(documentMock.getElementById("targetBox").textContent, "目标：-");
assert.match(context.history.lastUrl, /branch=feature%2Fremoved/);
assert.doesNotMatch(context.history.lastUrl, /method=/);

assert.ok(fetchCalls.some(call => call.path.includes("/branches/services?branch=main")));
assert.ok(fetchCalls.some(call => call.path.includes("/methods/queryStatus-c0927e9de749?branch=main")));
assert.ok(fetchCalls.some(call => call.path.includes("/branches/services?branch=feature%2Fremoved")));

context.__app.state.services = [{
  fqn: "com.company.loan.facade.LoanApplyFacade",
  status: "published",
  changeKind: "modified",
  methods: [{
    id: "submitApply-add-remove",
    name: "submitApply",
    changeKind: "modified",
    params: [{
      tree: {
        changeKind: "unchanged",
        children: [
          { changeKind: "unchanged", children: [] },
          { changeKind: "added", children: [] },
          { changeKind: "removed", children: [] }
        ]
      }
    }],
    returnTree: { changeKind: "unchanged", children: [] }
  }]
}, {
  fqn: "com.company.loan.facade.LoanQueryFacade",
  status: "published",
  changeKind: "modified",
  methods: [{
    id: "queryStatus-modified",
    name: "queryStatus",
    changeKind: "modified",
    params: [{
      tree: {
        changeKind: "unchanged",
        children: [{ changeKind: "modified", children: [] }]
      }
    }],
    returnTree: { changeKind: "unchanged", children: [] }
  }]
}];

const filteredServiceNames = () => Array.from(context.__app.filteredServices(), service => service.fqn);

context.__app.state.serviceFilter = "changed";
assert.deepEqual(filteredServiceNames(), [
  "com.company.loan.facade.LoanApplyFacade",
  "com.company.loan.facade.LoanQueryFacade"
]);
context.__app.state.serviceFilter = "added";
assert.deepEqual(filteredServiceNames(), [
  "com.company.loan.facade.LoanApplyFacade"
]);
context.__app.state.serviceFilter = "removed";
assert.deepEqual(filteredServiceNames(), [
  "com.company.loan.facade.LoanApplyFacade"
]);
context.__app.state.serviceFilter = "modified";
assert.deepEqual(filteredServiceNames(), [
  "com.company.loan.facade.LoanQueryFacade"
]);

console.log(JSON.stringify({
  status: "ok",
  checks: [
    "route-selected method loads initial branch",
    "branch switch refreshes same method from new snapshot",
    "branch switch clears method when method id is absent",
    "change filter keeps differences distinct from content modifications"
  ]
}, null, 2));
