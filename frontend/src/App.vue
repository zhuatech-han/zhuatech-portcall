<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import {
  Ship,
  Anchor,
  CalendarClock,
  ClipboardCheck,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Plus,
  Search,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  ExternalLink,
  Clock3,
  AlertCircle,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { states, actions, payload, localTime } from "./domain.js";
import { fields, commandFields } from "./forms.js";
const lang = ref(localStorage.getItem("portcall-language") || "zh"),
  me = ref(null),
  view = ref("calls"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  directories = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  kindFilter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  selected = ref(null),
  stats = ref({ callStates: {}, serviceStates: {} }),
  modal = ref(null),
  form = ref({}),
  contact = ref(false),
  eventPage = ref(0);
const t = (zh, en) => (lang.value === "zh" ? zh : en);
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("portcall-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
const business = ["calls", "changes", "services", "vessels", "agencies"],
  adminTypes = [
    "users",
    "roles",
    "departments",
    "menus",
    "permissions",
    "dictionaries",
    "settings",
  ];
const labels = {
  calls: ["船舶挂靠", "Port calls"],
  changes: ["计划变更", "Plan changes"],
  services: ["服务请求", "Services"],
  vessels: ["船舶目录", "Vessels"],
  agencies: ["协作机构", "Agencies"],
  dashboard: ["协同统计", "Statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["服务类型", "Service types"],
  settings: ["系统参数", "Settings"],
};
const icons = {
  calls: Ship,
  changes: CalendarClock,
  services: ClipboardCheck,
  vessels: Anchor,
  agencies: Users,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
};
const title = computed(() =>
    t(...(labels[view.value] || ["PortCall", "PortCall"])),
  ),
  staff = computed(() => !me.value?.agencyId),
  can = (p) => me.value?.permissions?.includes(p),
  statusName = (s) => t(...(states[s] || [s, s]));
const actionNames = {
  SAVE: ["保存记录", "Save"],
  PLAN_CHANGED: ["计划已变更", "Plan changed"],
  CALL_CANCELLED: ["挂靠取消联动", "Call cancellation"],
  submit: ["提交申请", "Submit"],
  cancel: ["取消记录", "Cancel"],
  approve: ["独立批准", "Approve"],
  reject: ["驳回申请", "Reject"],
  arrive: ["登记实际到港", "Record arrival"],
  depart: ["登记实际离港", "Record departure"],
  close: ["独立关单", "Close call"],
  request: ["发送服务请求", "Request service"],
  replan: ["重排并重新请求", "Reschedule request"],
  confirm: ["确认服务窗口", "Confirm window"],
  decline: ["拒绝服务请求", "Decline request"],
  start: ["登记作业开始", "Start work"],
  report: ["回报实际完成", "Report completion"],
  accept: ["独立验收通过", "Accept work"],
  dispute: ["提出验收异议", "Dispute work"],
};
const actionName = (a) => t(...(actionNames[a] || [a, a]));
const failures = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired. Sign in again"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  STAFF_ONLY: ["此操作限内部岗位", "Staff only"],
  AGENT_ONLY: ["此操作限船代或内部岗位", "Ship agent or staff only"],
  BOUND_PROVIDER_REQUIRED: [
    "须由绑定的服务方账号操作",
    "The bound service provider must act",
  ],
  OUT_OF_SCOPE: ["超出当前账号的数据范围", "Outside your data scope"],
  STALE_VERSION: [
    "记录已变化，请刷新后重试",
    "Record changed. Refresh before retrying",
  ],
  STALE_PLAN: ["计划版本已变化，请重新核对", "Plan revision changed"],
  INVALID_STATE: ["当前状态不允许此操作", "Unavailable in this state"],
  INDEPENDENT_REVIEW_REQUIRED: [
    "须由另一位授权人员完成",
    "A different authorized person must act",
  ],
  OUTSIDE_PLAN: [
    "请求窗口须在当前预计到离区间内",
    "Window must lie within the estimated call interval",
  ],
  INVALID_INTERVAL: [
    "结束须晚于开始，区间最多90日",
    "End must follow start, within 90 days",
  ],
  INVALID_TIME: ["请填写明确时间", "Enter a valid time"],
  INVALID_ACTUAL_TIME: [
    "实际时间不能在未来或早于前序事实",
    "Actual time cannot be in the future or before prior facts",
  ],
  AFTER_DEPARTURE: [
    "实际作业结束不能晚于已记录离港时间",
    "Work cannot end after recorded departure",
  ],
  UNRESOLVED_WORK: [
    "仍有未闭合任务，且须有已验收的关键服务",
    "Resolve outstanding tasks and accept a critical service",
  ],
  OPEN_CHANGE: ["已有开放变更，请先处理", "Resolve the existing change first"],
  UNCHANGED_PLAN: ["新计划时间与当前相同", "New times match the current plan"],
  IMMUTABLE_IDENTITY: [
    "已有记录的编号和身份不可更改",
    "Existing references and identities are fixed",
  ],
  INVALID_AGENT: [
    "船代或船舶不可用、类型或部门不匹配",
    "Agent/vessel unavailable or department mismatch",
  ],
  INVALID_PROVIDER: [
    "服务方不可用、类型或部门不匹配",
    "Provider unavailable or department mismatch",
  ],
  DISABLED_RESOURCE: ["关联目录已停用", "Related directory disabled"],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect username or password"],
  LOGIN_THROTTLED: ["登录尝试过多，稍后重试", "Too many attempts. Try later"],
  WEAK_PASSWORD: [
    "密码至少12位，含大小写字母和数字",
    "Use at least 12 characters, upper/lowercase and digits",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确", "Incorrect current password"],
  LAST_ADMIN: [
    "须保留一位未绑定机构的启用管理员",
    "Keep an enabled staff administrator",
  ],
  CONFLICT: [
    "编号重复或记录正在被引用",
    "Duplicate reference or referenced record",
  ],
  INVALID_INPUT: [
    "请检查必填项与输入格式",
    "Check required fields and formats",
  ],
  REQUEST_KEY_REUSED: [
    "此次请求已用于不同操作，请重新打开表单",
    "Request identifier conflicts. Reopen the form",
  ],
  CAPACITY_LIMIT: ["已达到此类资源上限", "Resource limit reached"],
};
function clearSession() {
  me.value = null;
  detail.value = null;
  rows.value = [];
  modal.value = null;
  options.value = {};
  directories.value = {};
  resetCsrf();
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") clearSession();
  } finally {
    busy.value = false;
  }
}
/** 读取当前可见目录与有限后台选项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function loadOptions() {
  options.value = await api("/options");
  if (can("admin") && staff.value && me.value.scope === "ALL")
    for (const k of ["roles", "permissions", "departments"])
      directories.value[k] = await api("/admin/" + k);
}
async function load() {
  detail.value = null;
  selected.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (business.includes(view.value)) {
    const r = await api(
      "/" +
        view.value +
        "?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          kind: kindFilter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = r.items;
    total.value = r.total;
  } else {
    let all = await api(
      view.value === "audit" ? "/audit" : "/admin/" + view.value,
    );
    all = all.filter((v) =>
      Object.values(v).some(
        (x) =>
          typeof x === "string" &&
          x.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort(
      sort.value === "reference"
        ? (a, b) =>
            String(a.name || a.username || a.code).localeCompare(
              String(b.name || b.username || b.code),
            )
        : (a, b) => b.id - a.id,
    );
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  if (busy.value) return;
  rows.value = [];
  detail.value = null;
  total.value = 0;
  stats.value = { callStates: {}, serviceStates: {} };
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  kindFilter.value = "";
  await run(load);
  window.scrollTo(0, 0);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    clearSession();
  });
}
async function open(row) {
  await run(async () => {
    selected.value = row.id;
    detail.value = await api("/" + view.value + "/" + row.id);
    eventPage.value = 0;
    window.scrollTo(0, 0);
  });
}
async function linked(type, row) {
  if (busy.value) return;
  view.value = type;
  rows.value = [];
  detail.value = null;
  selected.value = null;
  await open(row);
}
const record = computed(() => detail.value?.record),
  currentActions = computed(() =>
    actions(view.value, record.value, me.value, detail.value?.call),
  ),
  events = computed(() => [...(detail.value?.events || [])].reverse()),
  eventRows = computed(() =>
    events.value.slice(eventPage.value * 10, eventPage.value * 10 + 10),
  );
const modalFields = computed(() =>
  modal.value?.kind === "command"
    ? commandFields(modal.value.action)
    : modal.value?.kind === "password"
      ? [
          ["oldPassword", "原密码", "Current password", "password"],
          ["newPassword", "新密码", "New password", "password"],
        ]
      : fields[modal.value?.type] || [],
);
function choices(key) {
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((value) => ({
      value,
      label: {
        ALL: t("全部", "All"),
        DEPARTMENT: t("本部门", "Department"),
        SELF: t("本人创建挂靠", "Calls created by self"),
      }[value],
    }));
  if (key === "agencyKinds")
    return [
      { value: "AGENT", label: t("船舶代理", "Ship agent") },
      { value: "PROVIDER", label: t("服务提供方", "Service provider") },
    ];
  let list = directories.value[key] || options.value[key] || [];
  if (["agents", "providers"].includes(key)) {
    list = (options.value.agencies || []).filter(
      (a) => a.kind === (key === "agents" ? "AGENT" : "PROVIDER"),
    );
    const selectedCall = (options.value.calls || []).find(
      (c) => c.id === Number(form.value.callId),
    );
    if (selectedCall)
      list = list.filter((a) => a.departmentId === selectedCall.departmentId);
  }
  if (["plannedCalls", "serviceCalls"].includes(key))
    list = (options.value.calls || []).filter((c) =>
      (key === "plannedCalls" ? ["PLANNED"] : ["PLANNED", "ARRIVED"]).includes(
        c.status,
      ),
    );
  return list.map((v) => ({
    value: ["permissions", "serviceTypes"].includes(key) ? v.code : v.id,
    label:
      lang.value === "en" && v.nameEn
        ? v.nameEn
        : [v.reference, v.name || v.displayName || v.code]
            .filter(Boolean)
            .join(" · "),
  }));
}
function edit(type, row = null) {
  error.value = "";
  modal.value = { kind: "edit", type, id: row?.id };
  form.value = row
    ? { ...row }
    : {
        enabled: true,
        departmentId: me.value.departmentId,
        scope: "DEPARTMENT",
        permissions: [],
        type: "service",
        kind: type === "agencies" ? "AGENT" : "CARGO",
        critical: true,
        agentId: me.value.agencyKind === "AGENT" ? me.value.agencyId : "",
        agencyId: "",
        eta: localTime(new Date()),
        etd: localTime(new Date(Date.now() + 86400000)),
      };
  for (const f of fields[type] || [])
    if (f[3] === "datetime" && row) form.value[f[0]] = localTime(row[f[0]]);
  if (type === "users") form.value.password = "";
}
watch(
  () => form.value.callId,
  (id) => {
    if (!modal.value || modal.value.kind !== "edit" || modal.value.id) return;
    const c = (options.value.calls || []).find((x) => x.id === Number(id));
    if (!c) return;
    if (modal.value.type === "changes") {
      form.value.baseRevision = c.planRevision;
      form.value.eta = localTime(c.eta);
      form.value.etd = localTime(c.etd);
    }
    if (modal.value.type === "services") {
      form.value.windowStart = localTime(c.eta);
      form.value.windowEnd = localTime(c.etd);
    }
  },
);
function command(action) {
  error.value = "";
  modal.value = {
    kind: "command",
    type: view.value,
    action,
    id: selected.value,
  };
  form.value = {
    note: "",
    at: localTime(new Date()),
    windowStart: localTime(detail.value.call.eta),
    windowEnd: localTime(detail.value.call.etd),
  };
}
function remove(type, row) {
  error.value = "";
  modal.value = { kind: "delete", type, id: row.id };
  form.value = {};
}
function readonly(key) {
  return (
    modal.value?.kind === "edit" &&
    Boolean(modal.value.id) &&
    (["reference", "departmentId"].includes(key) ||
      (modal.value.type === "agencies" && key === "kind") ||
      (modal.value.type === "calls" && ["vesselId", "agentId"].includes(key)) ||
      (["services", "changes"].includes(modal.value.type) &&
        key === "callId") ||
      (modal.value.type === "dictionaries" && ["type", "code"].includes(key)))
  );
}
/** UUID在相同表单重试时保留，载荷变化才换新；版本由当前对象携带。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  await run(async () => {
    const m = modal.value;
    let body = payload(form.value, modalFields.value);
    if (m.kind === "password") {
      await api("/auth/password", "POST", body);
      clearSession();
      return;
    }
    if (m.kind === "delete")
      await api(
        "/" +
          (adminTypes.includes(m.type) ? "admin/" : "") +
          m.type +
          "/" +
          m.id,
        "DELETE",
        {},
      );
    else {
      if (business.includes(m.type)) {
        body.version =
          m.kind === "command"
            ? record.value.version
            : (form.value.version ?? null);
        const signature = JSON.stringify(body);
        if (signature !== m.signature) {
          m.requestKey = crypto.randomUUID();
          m.signature = signature;
        }
        body.requestKey = m.requestKey;
      }
      const result = await api(
        "/" +
          (adminTypes.includes(m.type) ? "admin/" : "") +
          m.type +
          (m.id ? "/" + m.id : "") +
          (m.kind === "command" ? "/commands/" + m.action : ""),
        m.kind === "edit" && m.id ? "PUT" : "POST",
        body,
      );
      if (m.kind === "command") detail.value = result;
    }
    modal.value = null;
    notice.value = t("已保存", "Saved");
    if (adminTypes.includes(m.type)) me.value = await api("/auth/me");
    await loadOptions();
    if (m.kind !== "command") await load();
  });
}
function formatTime(v) {
  return v
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).format(new Date(v))
    : "—";
}
const isTime = (key) =>
  [
    "eta",
    "etd",
    "arrivedAt",
    "departedAt",
    "windowStart",
    "windowEnd",
    "actualStart",
    "actualEnd",
    "createdAt",
  ].includes(key);
function fieldValue(row, key) {
  if (view.value === "settings" && key === "code")
    return t(
      ...({
        companyName: ["显示名称", "Display name"],
        timezone: ["业务时区", "Business timezone"],
        maxRecords: ["每类资源上限", "Resource limit per type"],
      }[row.code] || [row.code, row.code]),
    );
  if (isTime(key)) return formatTime(row[key]);
  if (key === "status") return statusName(row[key]);
  if (key === "kind")
    return row[key] === "AGENT"
      ? t("船舶代理", "Ship agent")
      : row[key] === "PROVIDER"
        ? t("服务提供方", "Provider")
        : choices("serviceTypes").find((c) => c.value === row[key])?.label ||
          row[key];
  if (key === "scope")
    return (
      choices("scope").find((c) => c.value === row[key])?.label || row[key]
    );
  if (["enabled", "critical"].includes(key))
    return row[key]
      ? t(
          key === "critical" ? "关键" : "启用",
          key === "critical" ? "Critical" : "Enabled",
        )
      : t(
          key === "critical" ? "一般" : "停用",
          key === "critical" ? "Optional" : "Disabled",
        );
  if (key === "permissions")
    return (row.permissions?.length || 0) + t(" 项权限", " permissions");
  const directory = {
    roleId: directories.value.roles,
    departmentId: directories.value.departments || options.value.departments,
    vesselId: options.value.vessels,
    agentId: options.value.agencies,
    providerId: options.value.agencies,
    agencyId: options.value.agencies,
    callId: options.value.calls,
  }[key];
  if (directory)
    return (
      directory.find((x) => x.id === row[key])?.name ||
      directory.find((x) => x.id === row[key])?.reference ||
      row[key] ||
      "—"
    );
  return row[key] ?? "—";
}
const columns = computed(
  () =>
    ({
      calls: [
        ["reference", "挂靠编号", "Reference"],
        ["vesselId", "船舶", "Vessel"],
        ["location", "作业地点", "Location"],
        ["eta", "预计到港", "ETA"],
        ["planRevision", "版本", "Plan"],
        ["status", "状态", "Status"],
      ],
      services: [
        ["reference", "服务编号", "Reference"],
        ["callId", "挂靠", "Call"],
        ["kind", "类型", "Type"],
        ["critical", "关键性", "Priority"],
        ["windowStart", "约定开始", "Window start"],
        ["status", "状态", "Status"],
      ],
      changes: [
        ["reference", "变更编号", "Reference"],
        ["callId", "挂靠", "Call"],
        ["baseRevision", "基线", "Base"],
        ["eta", "新预计到港", "New ETA"],
        ["status", "状态", "Status"],
      ],
      settings: [
        ["code", "参数", "Setting"],
        ["value", "参数值", "Value"],
      ],
      audit: [
        ["createdAt", "操作时间", "Time"],
        ["actor", "操作人", "Actor"],
        ["action", "操作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || []).filter((f) => f[0] !== "password").slice(0, 5),
);
const canCreate = computed(() =>
  business.includes(view.value)
    ? view.value === "calls"
      ? can("call.write") && me.value.agencyKind !== "PROVIDER"
      : view.value === "changes"
        ? can("change.write") && me.value.agencyKind !== "PROVIDER"
        : staff.value &&
          can(view.value === "services" ? "service.write" : "catalog.write")
    : staff.value &&
      can("admin") &&
      ["users", "roles", "departments", "dictionaries"].includes(view.value),
);
const canEdit = computed(() =>
  business.includes(view.value)
    ? view.value === "calls"
      ? canCreate.value
      : view.value === "changes"
        ? canCreate.value
        : staff.value &&
          can(view.value === "services" ? "service.write" : "catalog.write")
    : staff.value && can("admin"),
);
const canDelete = computed(() =>
  ["vessels", "agencies"].includes(view.value)
    ? staff.value && can("catalog.write")
    : staff.value &&
      can("admin") &&
      ["users", "roles", "departments", "dictionaries"].includes(view.value),
);
const editable = (r) =>
  ["vessels", "agencies"].includes(view.value) ||
  adminTypes.includes(view.value) ||
  r.status === "DRAFT";
const filterStates = computed(() =>
  ["vessels", "agencies"].includes(view.value)
    ? [
        ["true", "启用", "Enabled"],
        ["false", "停用", "Disabled"],
      ]
    : Object.keys(states)
        .filter((s) =>
          view.value === "calls"
            ? [
                "DRAFT",
                "SUBMITTED",
                "PLANNED",
                "ARRIVED",
                "DEPARTED",
                "CLOSED",
                "CANCELLED",
                "REJECTED",
              ].includes(s)
            : view.value === "changes"
              ? [
                  "DRAFT",
                  "SUBMITTED",
                  "APPROVED",
                  "REJECTED",
                  "CANCELLED",
                ].includes(s)
              : [
                  "DRAFT",
                  "REQUESTED",
                  "CONFIRMED",
                  "CHANGE_PENDING",
                  "IN_PROGRESS",
                  "REPORTED",
                  "ACCEPTED",
                  "DECLINED",
                  "CANCELLED",
                  "DISPUTED",
                ].includes(s),
        )
        .map((s) => [s, ...states[s]]),
);
async function downloadReport() {
  await run(async () => {
    const result = await api(
      "/" + view.value + "/" + selected.value + "/report.json",
    );
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(result, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = view.value + "-" + selected.value + ".json";
    a.click();
    URL.revokeObjectURL(url);
  });
}
let poll;
onMounted(async () => {
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
  try {
    me.value = await api("/auth/me");
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await run(load);
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") error.value = e.message;
  }
  poll = setInterval(async () => {
    if (!me.value || busy.value) return;
    try {
      me.value = await api("/auth/me");
      if (!me.value.menus.some((m) => m.code === view.value))
        await navigate(me.value.menus[0]?.code || "dashboard");
    } catch (e) {
      if (e.message === "UNAUTHENTICATED") clearSession();
    }
  }, 20000);
});
onUnmounted(() => clearInterval(poll));
</script>

<template>
  <div v-if="!me" class="login-layout">
    <section class="harbour-art" aria-hidden="true">
      <div class="art-word">PORT / CALL</div>
      <div class="harbour-lines"><i></i><i></i><i></i></div>
      <div class="vessel-art"><span></span><span></span><span></span></div>
      <div class="art-caption">
        {{ t("到港 · 服务 · 离港", "ARRIVAL · SERVICES · DEPARTURE") }}
      </div>
    </section>
    <main class="login-panel">
      <div class="login-top">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" /><button
          class="plain"
          @click="language"
        >
          {{ lang === "zh" ? "EN" : "中文" }}
        </button>
      </div>
      <div class="login-heading">
        <span class="eyebrow">PORTCALL</span>
        <h1>
          {{ t("船舶挂靠与服务协同", "Port calls & service coordination") }}
        </h1>
        <p>{{ t("登录你的业务工作台", "Sign in to your workspace") }}</p>
      </div>
      <form @submit.prevent="signIn">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <button class="primary login-submit" :disabled="busy">
          {{ busy ? t("正在登录", "Signing in") : t("登录", "Sign in")
          }}<ArrowRight :size="18" />
        </button>
      </form>
      <div class="login-footer">
        <button class="plain" @click="contact = true">
          {{ t("知华科技 · 商业咨询", "ZhuaTech · Commercial enquiries") }}
        </button>
        <p>
          {{
            t("公开源码学习版／非商业源码版", "Non-commercial source edition")
          }}
        </p>
      </div>
    </main>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
        <div>
          <strong>PortCall</strong
          ><span>{{ t("船舶挂靠协同", "Port call coordination") }}</span>
        </div>
      </div>
      <nav :aria-label="t('主要导航', 'Main navigation')">
        <template v-for="(m, i) in me.menus" :key="m.code"
          ><p
            v-if="i === 0 || m.code === 'dashboard' || m.code === 'users'"
            class="nav-section"
          >
            {{
              i === 0
                ? t("业务协同", "OPERATIONS")
                : m.code === "dashboard"
                  ? t("统计与审计", "OVERVIEW")
                  : t("系统管理", "ADMINISTRATION")
            }}
          </p>
          <button
            :class="{ active: view === m.code }"
            :disabled="busy"
            @click="navigate(m.code)"
          >
            <component :is="icons[m.code] || Settings" :size="18" /><span>{{
              lang === "en" ? m.nameEn : m.name
            }}</span>
          </button></template
        >
      </nav>
      <div class="sidebar-footer">
        <button class="plain" @click="contact = true">
          <ExternalLink :size="14" />{{
            t("知华科技 · 咨询", "ZhuaTech · Enquiries")
          }}</button
        ><span>{{ t("非商业源码版 0.1.0", "Non-commercial 0.1.0") }}</span>
      </div>
    </aside>
    <div class="work-area">
      <header class="topbar">
        <span>{{ options.companyName || "PortCall" }}</span>
        <div class="top-actions">
          <button class="plain" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button
            class="account-button"
            @click="
              modal = { kind: 'password' };
              form = {};
            "
          >
            <span class="avatar">{{ me.displayName?.slice(0, 1) }}</span
            ><span
              >{{ me.displayName }}<small>{{ me.role }}</small></span
            ></button
          ><button
            class="icon-button"
            :aria-label="t('退出', 'Sign out')"
            :disabled="busy"
            @click="signOut"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main class="main-content">
        <div class="page-heading">
          <div>
            <span class="eyebrow">{{
              me.agencyKind === "PROVIDER"
                ? t("服务方工作台", "PROVIDER WORKSPACE")
                : me.agencyKind === "AGENT"
                  ? t("船代工作台", "AGENT WORKSPACE")
                  : t("业务工作台", "OPERATIONS WORKSPACE")
            }}</span>
            <h1>{{ title }}</h1>
          </div>
          <div class="heading-tools">
            <button
              v-if="detail"
              class="secondary"
              :disabled="busy"
              @click="run(load)"
            >
              <ChevronLeft :size="16" />{{
                t("返回列表", "Back to list")
              }}</button
            ><button
              v-if="!detail && canCreate"
              class="primary"
              :disabled="busy"
              @click="edit(view)"
            >
              <Plus :size="17" />{{ t("新建", "New") }}
            </button>
          </div>
        </div>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <section v-if="view === 'dashboard'" class="dashboard">
          <div class="metric-grid">
            <article
              v-for="(item, index) in [
                [t('可见挂靠', 'Visible calls'), stats.calls, Ship],
                [
                  t('服务请求', 'Service requests'),
                  stats.services,
                  ClipboardCheck,
                ],
                [
                  t('需要重新排期', 'Rescheduling required'),
                  stats.replan,
                  CalendarClock,
                ],
                [
                  t('等待独立验收', 'Awaiting acceptance'),
                  stats.pendingReview,
                  ShieldCheck,
                ],
              ]"
              :key="index"
              class="metric"
            >
              <component :is="item[2]" :size="21" /><span>{{ item[0] }}</span
              ><strong>{{ item[1] ?? 0 }}</strong>
            </article>
          </div>
          <div class="chart-grid">
            <article class="panel">
              <h2>{{ t("挂靠进度", "Call progress") }}</h2>
              <div
                v-for="(n, s) in stats.callStates"
                :key="s"
                class="chart-row"
              >
                <span>{{ statusName(s) }}</span>
                <div>
                  <i
                    :style="{
                      width: (n / Math.max(stats.calls || 1, 1)) * 100 + '%',
                    }"
                  ></i>
                </div>
                <strong>{{ n }}</strong>
              </div>
              <p
                v-if="!Object.keys(stats.callStates || {}).length"
                class="empty"
              >
                {{ t("暂无挂靠记录", "No call records") }}
              </p>
            </article>
            <article class="panel">
              <h2>{{ t("服务进度", "Service progress") }}</h2>
              <div
                v-for="(n, s) in stats.serviceStates"
                :key="s"
                class="chart-row"
              >
                <span>{{ statusName(s) }}</span>
                <div>
                  <i
                    :style="{
                      width: (n / Math.max(stats.services || 1, 1)) * 100 + '%',
                    }"
                  ></i>
                </div>
                <strong>{{ n }}</strong>
              </div>
              <p
                v-if="!Object.keys(stats.serviceStates || {}).length"
                class="empty"
              >
                {{ t("暂无服务记录", "No service records") }}
              </p>
            </article>
          </div>
          <div class="attention-strip">
            <span
              >{{ t("未验收关键服务", "Open critical services") }}
              <b>{{ stats.criticalOpen ?? 0 }}</b></span
            ><span
              >{{ t("验收异议", "Disputed work") }}
              <b>{{ stats.disputed ?? 0 }}</b></span
            ><span
              >{{ t("业务时区", "Business timezone") }}
              <b>Asia/Shanghai</b></span
            >
          </div>
        </section>
        <section v-else-if="detail" class="detail-layout">
          <div class="detail-main">
            <article class="panel record-panel">
              <div class="record-heading">
                <div>
                  <span class="eyebrow">{{
                    view === "calls"
                      ? "PORT CALL"
                      : view === "services"
                        ? "SERVICE ORDER"
                        : "PLAN CHANGE"
                  }}</span>
                  <h2>{{ record.reference }}</h2>
                </div>
                <span class="status" :data-status="record.status">{{
                  statusName(record.status)
                }}</span>
              </div>
              <div class="record-meta">
                <span><Ship :size="17" />{{ detail.vessel.name }}</span
                ><span>{{ detail.agent.name }}</span
                ><span>{{ detail.call.location }}</span
                ><span
                  >{{ t("计划版本", "Plan revision") }}
                  {{ detail.call.planRevision }}</span
                >
              </div>
              <div v-if="view === 'calls'" class="time-grid">
                <article>
                  <span>{{ t("预计到港 ETA", "Estimated arrival") }}</span
                  ><strong>{{ formatTime(record.eta) }}</strong
                  ><small>{{ t("当前计划", "Current plan") }}</small>
                </article>
                <article>
                  <span>{{ t("预计离港 ETD", "Estimated departure") }}</span
                  ><strong>{{ formatTime(record.etd) }}</strong
                  ><small>{{ t("当前计划", "Current plan") }}</small>
                </article>
                <article class="actual">
                  <span>{{ t("实际到港 ATA", "Actual arrival") }}</span
                  ><strong>{{ formatTime(record.arrivedAt) }}</strong
                  ><small>{{ t("已核实事实", "Verified record") }}</small>
                </article>
                <article class="actual">
                  <span>{{ t("实际离港 ATD", "Actual departure") }}</span
                  ><strong>{{ formatTime(record.departedAt) }}</strong
                  ><small>{{ t("已核实事实", "Verified record") }}</small>
                </article>
              </div>
              <div v-else-if="view === 'services'">
                <div class="service-meta">
                  <span>{{ detail.provider.name }}</span
                  ><span class="priority">{{ fieldValue(record, "kind") }}</span
                  ><span
                    class="priority"
                    :class="{ critical: record.critical }"
                    >{{ fieldValue(record, "critical") }}</span
                  ><span
                    >{{ t("请求计划版本", "Requested plan revision") }}
                    {{ record.planRevision }}</span
                  >
                </div>
                <div v-if="record.status === 'CHANGE_PENDING'" class="notice">
                  {{
                    t(
                      "计划已变更，此服务须重排窗口并重新确认。",
                      "Plan changed. Reschedule this service and obtain a new confirmation.",
                    )
                  }}
                </div>
                <div class="time-grid">
                  <article>
                    <span>{{ t("约定开始", "Window start") }}</span
                    ><strong>{{ formatTime(record.windowStart) }}</strong>
                  </article>
                  <article>
                    <span>{{ t("约定结束", "Window end") }}</span
                    ><strong>{{ formatTime(record.windowEnd) }}</strong>
                  </article>
                  <article class="actual">
                    <span>{{ t("实际开始", "Actual start") }}</span
                    ><strong>{{ formatTime(record.actualStart) }}</strong>
                  </article>
                  <article class="actual">
                    <span>{{ t("实际结束", "Actual end") }}</span
                    ><strong>{{ formatTime(record.actualEnd) }}</strong>
                  </article>
                </div>
              </div>
              <div v-else class="time-grid">
                <article>
                  <span>{{ t("新预计到港 ETA", "Proposed arrival") }}</span
                  ><strong>{{ formatTime(record.eta) }}</strong>
                </article>
                <article>
                  <span>{{ t("新预计离港 ETD", "Proposed departure") }}</span
                  ><strong>{{ formatTime(record.etd) }}</strong>
                </article>
                <article class="actual">
                  <span>{{
                    t("当前预计到港", "Current estimated arrival")
                  }}</span
                  ><strong>{{ formatTime(detail.call.eta) }}</strong>
                </article>
                <article class="actual">
                  <span>{{
                    t("当前预计离港", "Current estimated departure")
                  }}</span
                  ><strong>{{ formatTime(detail.call.etd) }}</strong>
                </article>
              </div>
              <div class="record-footer">
                <span>{{
                  t(
                    "上海时区 · 操作记录",
                    "Shanghai time · Operational records",
                  )
                }}</span
                ><button
                  v-if="can('export')"
                  class="plain"
                  :disabled="busy"
                  @click="downloadReport"
                >
                  <Download :size="15" />{{
                    t("下载业务证据", "Download evidence")
                  }}
                </button>
              </div>
            </article>
            <article v-if="view === 'calls'" class="panel">
              <h2>{{ t("关联服务", "Related services") }}</h2>
              <button
                v-for="s in detail.services"
                :key="s.id"
                class="related-row"
                :disabled="busy"
                @click="linked('services', s)"
              >
                <div>
                  <strong>{{ s.reference }}</strong
                  ><span
                    >{{ fieldValue(s, "kind") }} ·
                    {{ fieldValue(s, "critical") }} ·
                    {{ formatTime(s.windowStart) }}</span
                  >
                </div>
                <span class="status" :data-status="s.status">{{
                  statusName(s.status)
                }}</span
                ><ChevronRight :size="17" />
              </button>
              <p v-if="!detail.services?.length" class="empty">
                {{ t("暂无关联服务", "No related services") }}
              </p>
              <template v-if="detail.changes?.length"
                ><h2 class="section-spaced">
                  {{ t("计划变更", "Plan changes") }}
                </h2>
                <button
                  v-for="c in detail.changes"
                  :key="c.id"
                  class="related-row"
                  :disabled="busy"
                  @click="linked('changes', c)"
                >
                  <strong>{{ c.reference }}</strong
                  ><span class="status" :data-status="c.status">{{
                    statusName(c.status)
                  }}</span
                  ><ChevronRight :size="17" /></button
              ></template>
            </article>
            <article class="panel timeline-panel">
              <div class="panel-heading">
                <h2>{{ t("核实与操作记录", "Evidence & activity") }}</h2>
                <span>{{ events.length }} {{ t("条", "records") }}</span>
              </div>
              <ol class="timeline">
                <li v-for="e in eventRows" :key="e.id">
                  <i></i>
                  <div>
                    <header>
                      <strong>{{ actionName(e.action) }}</strong
                      ><time>{{ formatTime(e.createdAt) }}</time>
                    </header>
                    <p>{{ e.note || t("记录已保存", "Record saved") }}</p>
                    <small>{{ t("操作账号", "Actor") }} #{{ e.actorId }}</small>
                  </div>
                </li>
              </ol>
              <div v-if="events.length > 10" class="pagination">
                <button
                  class="icon-button"
                  :disabled="eventPage === 0"
                  @click="eventPage--"
                >
                  <ChevronLeft :size="17" /></button
                ><span
                  >{{ eventPage + 1 }} /
                  {{ Math.ceil(events.length / 10) }}</span
                ><button
                  class="icon-button"
                  :disabled="(eventPage + 1) * 10 >= events.length"
                  @click="eventPage++"
                >
                  <ChevronRight :size="17" />
                </button>
              </div>
            </article>
          </div>
          <aside class="action-panel panel">
            <h2>{{ t("当前操作", "Available actions") }}</h2>
            <span class="status" :data-status="record.status">{{
              statusName(record.status)
            }}</span
            ><button
              v-if="canEdit && editable(record)"
              class="secondary"
              :disabled="busy"
              @click="edit(view, record)"
            >
              {{ t("编辑草稿", "Edit draft") }}</button
            ><button
              v-for="a in currentActions"
              :key="a"
              class="primary"
              :disabled="busy"
              @click="command(a)"
            >
              {{ actionName(a) }}<ArrowRight :size="16" />
            </button>
            <p
              v-if="!currentActions.length && !(canEdit && editable(record))"
              class="muted"
            >
              {{
                t(
                  "当前岗位暂无可执行动作",
                  "No available actions for your role",
                )
              }}
            </p>
            <div class="action-note">
              <span>{{ t("记录版本", "Record version") }}</span
              ><strong>{{ record.version }}</strong>
            </div>
          </aside>
        </section>
        <section v-else class="panel list-panel">
          <form
            class="filters"
            @submit.prevent="
              page = 0;
              run(load);
            "
          >
            <label class="search-box"
              ><Search :size="17" /><input
                v-model="search"
                :placeholder="t('搜索编号、名称', 'Search reference or name')"
                :aria-label="t('搜索', 'Search')"
                maxlength="200" /></label
            ><select
              v-if="business.includes(view)"
              v-model="filter"
              :aria-label="t('状态筛选', 'Filter status')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option v-for="s in filterStates" :key="s[0]" :value="s[0]">
                {{ t(s[1], s[2]) }}
              </option></select
            ><select
              v-if="['agencies', 'services'].includes(view)"
              v-model="kindFilter"
              :aria-label="t('类型筛选', 'Filter type')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="">{{ t("全部类型", "All types") }}</option>
              <option
                v-for="c in choices(
                  view === 'agencies' ? 'agencyKinds' : 'serviceTypes',
                )"
                :key="c.value"
                :value="c.value"
              >
                {{ c.label }}
              </option></select
            ><select
              v-model="sort"
              :aria-label="t('排序', 'Sort')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="newest">{{ t("最近新增", "Newest") }}</option>
              <option value="reference">
                {{ t("编号／名称", "Reference / name") }}
              </option></select
            ><button class="secondary" :disabled="busy">
              {{ t("查询", "Search") }}</button
            ><button
              class="icon-button"
              type="button"
              :aria-label="t('刷新', 'Refresh')"
              :disabled="busy"
              @click="run(load)"
            >
              <RefreshCw :size="17" />
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="f in columns" :key="f[0]">{{ t(f[1], f[2]) }}</th>
                  <th v-if="view !== 'audit'">{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id">
                  <td v-for="f in columns" :key="f[0]">
                    <span
                      v-if="f[0] === 'status'"
                      class="status"
                      :data-status="row.status"
                      >{{ statusName(row.status) }}</span
                    ><span
                      v-else
                      :class="{ reference: f[0] === 'reference' }"
                      :title="String(fieldValue(row, f[0]))"
                      >{{ fieldValue(row, f[0]) }}</span
                    >
                  </td>
                  <td v-if="view !== 'audit'" class="row-actions">
                    <button
                      v-if="['calls', 'services', 'changes'].includes(view)"
                      class="row-button"
                      :disabled="busy"
                      @click="open(row)"
                    >
                      {{ t("查看", "View") }}<ArrowRight :size="14" /></button
                    ><button
                      v-if="canEdit && editable(row)"
                      class="row-button"
                      :disabled="busy"
                      @click="edit(view, row)"
                    >
                      {{ t("编辑", "Edit") }}</button
                    ><button
                      v-if="canDelete"
                      class="row-button danger"
                      :disabled="busy"
                      @click="remove(view, row)"
                    >
                      {{ t("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td :colspan="columns.length + 1" class="empty">
                    {{
                      busy
                        ? t("正在加载", "Loading")
                        : t("暂无记录", "No records")
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <span>{{ total }} {{ t("条记录", "records") }}</span>
            <div>
              <button
                class="icon-button"
                :disabled="page === 0 || busy"
                :aria-label="t('上一页', 'Previous page')"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="17" /></button
              ><span
                >{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 12)) }}</span
              ><button
                class="icon-button"
                :disabled="(page + 1) * 12 >= total || busy"
                :aria-label="t('下一页', 'Next page')"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="17" />
              </button>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
  <div
    v-if="modal"
    class="modal-backdrop"
    @click.self="!busy && (modal = null)"
  >
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="
        modal.kind === 'password'
          ? t('修改密码', 'Change password')
          : modal.kind === 'command'
            ? actionName(modal.action)
            : t(...(labels[modal.type] || ['记录', 'Record']))
      "
    >
      <header>
        <h2>
          {{
            modal.kind === "password"
              ? t("修改密码", "Change password")
              : modal.kind === "delete"
                ? t("删除记录", "Delete record")
                : modal.kind === "command"
                  ? actionName(modal.action)
                  : t(
                      modal.id ? "编辑记录" : "新建记录",
                      modal.id ? "Edit record" : "New record",
                    )
          }}
        </h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          :disabled="busy"
          @click="modal = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="save">
        <p v-if="modal.kind === 'delete'" class="delete-note">
          {{
            t(
              "只可删除未被引用的目录记录。",
              "Only unreferenced directory records can be deleted.",
            )
          }}
        </p>
        <div v-else class="form-grid">
          <template v-for="f in modalFields" :key="f[0]"
            ><fieldset v-if="f[3] === 'permissions'" class="permissions-field">
              <legend>{{ t(f[1], f[2]) }}</legend>
              <label
                v-for="p in directories.permissions || []"
                :key="p.code"
                class="check"
                ><input
                  v-model="form.permissions"
                  type="checkbox"
                  :value="p.code"
                /><span
                  >{{ p.name }}<small>{{ p.code }}</small></span
                ></label
              >
            </fieldset>
            <label v-else-if="f[3] === 'boolean'" class="check"
              ><input v-model="form[f[0]]" type="checkbox" /><span>{{
                t(f[1], f[2])
              }}</span></label
            ><label v-else :class="{ 'full-width': f[3] === 'textarea' }"
              >{{ t(f[1], f[2])
              }}<select
                v-if="['id', 'select'].includes(f[3])"
                v-model="form[f[0]]"
                :disabled="readonly(f[0])"
                :required="!['agencyId'].includes(f[0])"
              >
                <option value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="c in choices(f[4])"
                  :key="c.value"
                  :value="c.value"
                >
                  {{ c.label }}
                </option></select
              ><textarea
                v-else-if="f[3] === 'textarea'"
                v-model="form[f[0]]"
                required
                maxlength="1000"
                rows="3"
              ></textarea
              ><input
                v-else
                v-model="form[f[0]]"
                :type="
                  f[3] === 'datetime'
                    ? 'datetime-local'
                    : f[3] === 'integer'
                      ? 'number'
                      : f[3] === 'password'
                        ? 'password'
                        : 'text'
                "
                :step="
                  f[3] === 'datetime' ? 1 : f[3] === 'integer' ? 1 : undefined
                "
                :readonly="
                  readonly(f[0]) ||
                  (modal.type === 'changes' && f[0] === 'baseRevision')
                "
                :required="
                  !(modal.type === 'users' && modal.id && f[0] === 'password')
                "
                :autocomplete="f[3] === 'password' ? 'new-password' : 'off'"
                :maxlength="f[3] === 'password' ? 72 : 200" /></label
          ></template>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <footer>
          <button
            class="secondary"
            type="button"
            :disabled="busy"
            @click="modal = null"
          >
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("正在保存", "Saving") : t("确认保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="contact" class="modal-backdrop" @click.self="contact = false">
    <section
      class="modal contact-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('联系知华科技', 'Contact ZhuaTech')"
    >
      <header>
        <h2>{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="20" />
        </button>
      </header>
      <img class="contact-logo" src="/brand/logo.jpg" alt="知华科技 LOGO" />
      <p>知华科技（上海如静知华信息科技有限公司）</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >https://www.zhuatech.cn/ <ExternalLink :size="15"
      /></a>
      <p>
        {{
          t(
            "商业授权、定制开发、部署与系统集成咨询",
            "Commercial licensing, custom development, deployment and integration",
          )
        }}
      </p>
      <div class="qr-grid">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
          <figcaption>微信 zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
          <figcaption>微信 zhuatech2</figcaption>
        </figure>
      </div>
      <p class="license-note">
        {{
          t(
            "公开源码学习版／非商业源码版。未经书面授权不得商用。",
            "Non-commercial source edition. Commercial use requires written authorization.",
          )
        }}
      </p>
    </section>
  </div>
</template>
