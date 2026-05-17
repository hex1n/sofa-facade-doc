#!/usr/bin/env node

const baseUrl = trimSlash(process.env.SOFA_DOC_BASE_URL || process.argv[2] || "http://127.0.0.1:18081");
const token = process.env.SOFA_DOC_TOKEN || process.argv[3] || "";
const projectId = process.env.SOFA_DOC_PROJECT || "";
const branchName = process.env.SOFA_DOC_BRANCH || "";
const scanWhenMissing = process.env.SOFA_DOC_SMOKE_SCAN === "1";
const expectConfigAccess = process.env.SOFA_DOC_SMOKE_CONFIG === "1";
const expectConfigForbidden = process.env.SOFA_DOC_SMOKE_CONFIG_FORBIDDEN === "1";
const expectedConfigProjects = splitList(process.env.SOFA_DOC_CONFIG_PROJECTS || "");
const forbiddenProject = process.env.SOFA_DOC_FORBIDDEN_PROJECT || "";

function trimSlash(value) {
  return value.replace(/\/+$/, "");
}

function fail(message) {
  throw new Error(message);
}

function expect(condition, message) {
  if (!condition) fail(message);
}

function encode(value) {
  return encodeURIComponent(value);
}

function splitList(value) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

async function http(path, options = {}) {
  const headers = Object.assign({}, options.headers || {});
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
  const response = await fetch(`${baseUrl}${path}`, Object.assign({}, options, { headers }));
  const body = await response.text();
  if (!response.ok) {
    fail(`${options.method || "GET"} ${path} failed: ${response.status} ${body}`);
  }
  return body;
}

async function status(path, options = {}) {
  const headers = Object.assign({}, options.headers || {});
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
  const response = await fetch(`${baseUrl}${path}`, Object.assign({}, options, { headers }));
  return { status: response.status, body: await response.text() };
}

async function json(path, options = {}) {
  return JSON.parse(await http(path, options));
}

async function scan(project, branch) {
  return json(`/api/projects/${encode(project)}/branches/scan?branch=${encode(branch)}`, { method: "POST" });
}

async function loadServices(project, branch) {
  try {
    return await json(`/api/projects/${encode(project)}/branches/services?branch=${encode(branch)}`);
  } catch (error) {
    if (!scanWhenMissing) {
      fail(`${error.message}\nNo snapshot is available. Run a scan first, or set SOFA_DOC_SMOKE_SCAN=1 to let this smoke test scan the branch.`);
    }
    await scan(project, branch);
    return json(`/api/projects/${encode(project)}/branches/services?branch=${encode(branch)}`);
  }
}

async function main() {
  expect(token, "SOFA_DOC_TOKEN or argv[3] is required");

  const index = await http("/index.html");
  expect(index.includes("SOFABoot Facade 文档平台"), "index title is missing");
  for (const id of ["configBtn", "projectSelect", "branchSelect", "serviceList", "docPane", "argsEditor", "invokeBtn"]) {
    expect(index.includes(`id="${id}"`), `index is missing #${id}`);
  }

  const app = await http("/app.js");
  for (const marker of ["branchSelect", "refreshSelectedMethod(previousMethodId", "copyMd", "downloadMd", "invokeBtn", "saveCaseBtn"]) {
    expect(app.includes(marker), `app.js is missing ${marker}`);
  }
  const configWorkbench = await http("/config-workbench.js");
  for (const marker of ["/api/config/projects", "/api/config/projects/validate", "createConfigWorkbench"]) {
    expect(configWorkbench.includes(marker), `config-workbench.js is missing ${marker}`);
  }

  const styles = await http("/styles.css");
  for (const marker of [".sidebar", ".content", ".invoke", "@media (max-width: 820px)"]) {
    expect(styles.includes(marker), `styles.css is missing ${marker}`);
  }

  const projects = await json("/api/projects");
  expect(Array.isArray(projects) && projects.length > 0, "no project is visible for this token");
  const project = projectId ? projects.find((item) => item.id === projectId) : projects[0];
  expect(project, `project ${projectId} is not visible for this token`);

  let editableProjectCount = null;
  if (expectConfigAccess) {
    const editable = await json("/api/config/projects");
    expect(Array.isArray(editable.projects), "config projects response is missing projects array");
    expect(editable.projects.some((item) => item.id === project.id), `config projects does not include ${project.id}`);
    if (expectedConfigProjects.length) {
      const actual = editable.projects.map((item) => item.id).sort();
      const expected = [...expectedConfigProjects].sort();
      expect(JSON.stringify(actual) === JSON.stringify(expected), `config projects mismatch: expected ${expected.join(",")}, got ${actual.join(",")}`);
    }
    editableProjectCount = editable.projects.length;
  }
  if (expectConfigForbidden) {
    const denied = await status("/api/config/projects");
    expect(denied.status === 403, `config projects should be forbidden, got ${denied.status}: ${denied.body}`);
  }

  const branches = await json(`/api/projects/${encode(project.id)}/branches`);
  expect(Array.isArray(branches) && branches.length > 0, `project ${project.id} has no branch`);
  const branch = branchName || branches[0];
  expect(branches.includes(branch), `branch ${branch} is not available for project ${project.id}`);

  const services = await loadServices(project.id, branch);
  expect(Array.isArray(services), "services response is not an array");
  const service = services.find((item) => Array.isArray(item.methods) && item.methods.length > 0);
  expect(service, `branch ${branch} has no service method in the latest snapshot`);
  const method = service.methods[0];

  const detail = await json(`/api/projects/${encode(project.id)}/methods/${encode(method.id)}?branch=${encode(branch)}`);
  expect(detail.snapshot && detail.snapshot.branch === branch, "method detail returned a different branch");
  expect(detail.service && detail.service.fqn === service.fqn, "method detail returned a different service");
  expect(detail.method && detail.method.id === method.id, "method detail returned a different method");

  const markdown = await http(`/api/projects/${encode(project.id)}/methods/${encode(method.id)}/markdown?branch=${encode(branch)}`);
  expect(markdown.includes(service.fqn), "markdown is missing the service FQN");
  expect(markdown.includes(detail.method.name), "markdown is missing the method name");

  if (forbiddenProject) {
    const denied = await status(`/api/projects/${encode(forbiddenProject)}/branches`);
    expect(denied.status === 403, `forbidden project ${forbiddenProject} should be denied, got ${denied.status}: ${denied.body}`);
  }

  console.log(JSON.stringify({
    status: "ok",
    baseUrl,
    project: project.id,
    branch,
    service: service.fqn,
    method: detail.method.name,
    methodId: method.id,
    configProjects: editableProjectCount,
    configForbidden: expectConfigForbidden || undefined,
    forbiddenProject: forbiddenProject || undefined
  }, null, 2));
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
