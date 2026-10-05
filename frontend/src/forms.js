// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 有限业务和管理表单；实际时间只在核实动作中输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const fields = {
  calls: [
    ["reference", "挂靠编号", "Call reference"],
    ["vesselId", "船舶", "Vessel", "id", "vessels"],
    ["agentId", "船舶代理", "Ship agent", "id", "agents"],
    ["location", "港区／作业地点", "Port / operational location"],
    ["eta", "预计到港 ETA", "Estimated arrival", "datetime"],
    ["etd", "预计离港 ETD", "Estimated departure", "datetime"],
  ],
  changes: [
    ["reference", "变更编号", "Change reference"],
    ["callId", "关联挂靠", "Port call", "id", "plannedCalls"],
    ["baseRevision", "当前计划版本", "Base plan revision", "integer"],
    ["eta", "新预计到港 ETA", "New estimated arrival", "datetime"],
    ["etd", "新预计离港 ETD", "New estimated departure", "datetime"],
  ],
  services: [
    ["reference", "服务请求编号", "Service reference"],
    ["callId", "关联挂靠", "Port call", "id", "serviceCalls"],
    ["providerId", "服务提供方", "Provider", "id", "providers"],
    ["kind", "服务类型", "Service type", "select", "serviceTypes"],
    [
      "critical",
      "关键服务（关单前须验收）",
      "Critical service (acceptance required)",
      "boolean",
    ],
    [
      "windowStart",
      "约定开始（上海时区）",
      "Window start (Shanghai)",
      "datetime",
    ],
    ["windowEnd", "约定结束（上海时区）", "Window end (Shanghai)", "datetime"],
  ],
  vessels: [
    ["reference", "内部船舶编号", "Vessel reference"],
    ["name", "船舶名称", "Vessel name"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  agencies: [
    ["reference", "机构编号", "Agency reference"],
    ["name", "机构名称", "Agency name"],
    ["kind", "机构类型", "Agency type", "select", "agencyKinds"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时留空保留）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "id", "roles"],
    ["departmentId", "部门", "Department", "id", "departments"],
    [
      "agencyId",
      "绑定机构（内部人员留空）",
      "Agency binding (staff leave blank)",
      "id",
      "agencies",
    ],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "select", "scope"],
    ["permissions", "接口权限", "API permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    [
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ],
    ["position", "排序", "Order", "integer"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission description"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};
/** 事实时刻、重排窗口和核实凭据分别收集，所有动作均要求说明。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function commandFields(action) {
  const note = [
    "note",
    "凭据编号／核实说明",
    "Evidence reference / note",
    "textarea",
  ];
  if (["arrive", "depart", "start", "report"].includes(action))
    return [
      ["at", "实际发生时间（上海时区）", "Actual time (Shanghai)", "datetime"],
      note,
    ];
  if (action === "replan")
    return [
      [
        "windowStart",
        "新约定开始（上海时区）",
        "New window start (Shanghai)",
        "datetime",
      ],
      [
        "windowEnd",
        "新约定结束（上海时区）",
        "New window end (Shanghai)",
        "datetime",
      ],
      note,
    ];
  return [note];
}
