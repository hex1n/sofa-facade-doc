function createConfigWorkbench(deps) {
  const state = deps.state;
  const $ = deps.$;
  const api = deps.api;
  const init = deps.init;
  const setConfigResult = deps.setConfigResult;
  const errorMessage = deps.errorMessage;
  const escapeHtml = deps.escapeHtml;
  const escapeAttr = deps.escapeAttr;

  async function open() {
    $("configModal").classList.remove("hidden");
    $("configTitle").textContent = "项目与分支配置";
    $("configPath").textContent = "读取中...";
    $("configEditor").value = "";
    setConfigResult("");
    try {
      const data = await api("/api/config/projects");
      state.configData = data;
      state.configIndex = 0;
      const teamText = (data.teams || []).map(team => `${team.displayName || team.id}(${team.id})`).join("、");
      $("configPath").textContent = data.admin ? "admin：可管理全部团队项目" : `团队：${teamText || "-"}`;
      render();
    } catch (e) {
      state.configData = null;
      $("configPath").textContent = "-";
      $("configEditor").value = "";
      $("configProjectList").innerHTML = "";
      const message = errorMessage(e) === "forbidden" ? "需要 admin token 或团队 token 才能管理项目与分支配置" : errorMessage(e);
      setConfigResult(message, true);
    }
  }

  function close() {
    $("configModal").classList.add("hidden");
    setConfigResult("");
  }

  async function save() {
    const original = $("configSave").textContent;
    $("configSave").textContent = "保存中...";
    $("configSave").disabled = true;
    setConfigResult("");
    try {
      syncCurrentForm();
      const parsed = JSON.parse($("configEditor").value);
      if (!parsed || !Array.isArray(parsed.projects)) throw new Error("配置必须包含 projects 数组");
      const validation = await api("/api/config/projects/validate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ projects: parsed.projects })
      });
      if (!validation.ok) {
        throw new Error((validation.errors || []).map(e => `${e.project || "-"} ${e.field}: ${e.message}`).join("；"));
      }
      const data = await api("/api/config/projects", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ projects: parsed.projects })
      });
      state.configData = data;
      state.configIndex = Math.min(state.configIndex, Math.max((data.projects || []).length - 1, 0));
      render();
      setConfigResult("项目与分支配置已保存并生效");
      await init();
    } catch (e) {
      setConfigResult(errorMessage(e), true);
    } finally {
      $("configSave").textContent = original;
      $("configSave").disabled = false;
    }
  }

  function render() {
    const data = state.configData || { projects: [], teams: [] };
    const projects = data.projects || [];
    if (!projects.length) {
      projects.push(newProject());
      state.configIndex = 0;
    }
    if (state.configIndex >= projects.length) state.configIndex = projects.length - 1;
    $("configProjectList").innerHTML = projects.map((project, index) => `
      <button type="button" class="config-project-row ${index === state.configIndex ? "active" : ""}" data-index="${index}">
        <strong>${escapeHtml(project.displayName || project.id || "未命名项目")}</strong>
        <span>${escapeHtml(project.id || "new-project")} / ${escapeHtml(project.team || "-")}</span>
      </button>
    `).join("");
    document.querySelectorAll(".config-project-row").forEach(el => el.onclick = () => {
      syncCurrentForm();
      state.configIndex = Number(el.dataset.index);
      render();
    });
    $("cfgTeam").innerHTML = (data.teams || []).map(team => `<option value="${escapeAttr(team.id)}">${escapeHtml(team.displayName || team.id)}</option>`).join("");
    renderForm(projects[state.configIndex] || newProject());
    updatePayload();
  }

  function renderForm(project) {
    const data = state.configData || {};
    const locked = !data.admin && !!project.id && (data.projects || []).some(p => p.id === project.id);
    setValue("cfgId", project.id || "");
    setValue("cfgTeam", project.team || ((data.teams || [])[0] && data.teams[0].id) || "");
    setValue("cfgDisplayName", project.displayName || "");
    setValue("cfgRepo", project.repo || "");
    setValue("cfgBaselineBranch", project.baselineBranch || "");
    setValue("cfgTokens", listText(project.tokens));
    setValue("cfgBranchesInclude", listText(project.branches && project.branches.include));
    setValue("cfgBranchesExclude", listText(project.branches && project.branches.exclude));
    setValue("cfgBranchesMax", String((project.branches && project.branches.maxMatched) || 20));
    setValue("cfgDirectUrl", project.branchDefaults && project.branchDefaults.directUrl || "");
    setValue("cfgUniqueId", project.branchDefaults && project.branchDefaults.uniqueId || "");
    setValue("cfgVersion", project.branchDefaults && project.branchDefaults.version || "");
    setValue("cfgTargetAppName", project.branchDefaults && project.branchDefaults.targetAppName || "");
    setValue("cfgSpringProfiles", listText(project.branchDefaults && project.branchDefaults.springProfiles));
    setValue("cfgSourceRoots", listText(project.sourceRoots));
    setValue("cfgResourceRoots", listText(project.resourceRoots));
    setValue("cfgFacadePackages", listText(project.facadePackages));
    $("cfgBranchOverrides").value = JSON.stringify(project.branchOverrides || {}, null, 2);
    $("cfgTeam").disabled = !data.admin;
    $("cfgRepo").disabled = locked;
    $("cfgTokens").disabled = locked;
    $("cfgId").disabled = locked;
    configFields().forEach(id => {
      const el = $(id);
      el.oninput = updatePayload;
      el.onchange = updatePayload;
    });
    $("cfgDisplayName").focus();
  }

  function syncCurrentForm() {
    if (!state.configData) return;
    const projects = state.configData.projects || [];
    if (!projects.length) return;
    projects[state.configIndex] = readForm();
    updatePayload();
  }

  function readForm() {
    let branchOverrides = {};
    const rawOverrides = $("cfgBranchOverrides").value.trim();
    if (rawOverrides) branchOverrides = JSON.parse(rawOverrides);
    return {
      id: $("cfgId").value.trim(),
      team: $("cfgTeam").value.trim(),
      displayName: $("cfgDisplayName").value.trim(),
      repo: $("cfgRepo").value.trim(),
      baselineBranch: $("cfgBaselineBranch").value.trim(),
      tokens: listValue("cfgTokens"),
      branches: {
        include: listValue("cfgBranchesInclude"),
        exclude: listValue("cfgBranchesExclude"),
        maxMatched: Number($("cfgBranchesMax").value || 20)
      },
      branchDefaults: {
        directUrl: $("cfgDirectUrl").value.trim(),
        uniqueId: $("cfgUniqueId").value.trim(),
        version: $("cfgVersion").value.trim(),
        targetAppName: $("cfgTargetAppName").value.trim(),
        springProfiles: listValue("cfgSpringProfiles")
      },
      sourceRoots: listValue("cfgSourceRoots"),
      resourceRoots: listValue("cfgResourceRoots"),
      facadePackages: listValue("cfgFacadePackages"),
      branchOverrides
    };
  }

  function addProject() {
    if (!state.configData) return;
    syncCurrentForm();
    state.configData.projects = state.configData.projects || [];
    state.configData.projects.push(newProject());
    state.configIndex = state.configData.projects.length - 1;
    render();
  }

  function newProject() {
    const team = state.configData && state.configData.teams && state.configData.teams[0] ? state.configData.teams[0].id : "";
    return {
      id: "",
      team,
      displayName: "",
      repo: "",
      baselineBranch: "main",
      tokens: [],
      branches: { include: ["main"], exclude: [], maxMatched: 20 },
      branchDefaults: { directUrl: "", springProfiles: ["test"] },
      sourceRoots: ["facade/src/main/java"],
      resourceRoots: ["service/src/main/resources"],
      facadePackages: [],
      branchOverrides: {}
    };
  }

  function updatePayload() {
    if (!state.configData) return;
    try {
      const projects = (state.configData.projects || []).slice();
      if (projects.length) projects[state.configIndex] = readForm();
      $("configEditor").value = JSON.stringify({ projects }, null, 2);
    } catch (e) {
      $("configEditor").value = "";
    }
  }

  function configFields() {
    return ["cfgId", "cfgTeam", "cfgDisplayName", "cfgRepo", "cfgBaselineBranch", "cfgTokens", "cfgBranchesInclude", "cfgBranchesExclude", "cfgBranchesMax", "cfgDirectUrl", "cfgUniqueId", "cfgVersion", "cfgTargetAppName", "cfgSpringProfiles", "cfgSourceRoots", "cfgResourceRoots", "cfgFacadePackages", "cfgBranchOverrides"];
  }

  function setValue(id, value) {
    $(id).value = value == null ? "" : String(value);
  }

  function listText(values) {
    return (values || []).join(", ");
  }

  function listValue(id) {
    return $(id).value.split(",").map(v => v.trim()).filter(Boolean);
  }

  return { open, close, save, addProject };
}
