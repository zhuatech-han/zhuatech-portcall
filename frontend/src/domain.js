// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 当前状态显示，业务校验始终由服务端执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  SUBMITTED: ["待审核", "Submitted"],
  PLANNED: ["已计划", "Planned"],
  ARRIVED: ["已到港", "Arrived"],
  DEPARTED: ["已离港", "Departed"],
  CLOSED: ["已关单", "Closed"],
  CANCELLED: ["已取消", "Cancelled"],
  APPROVED: ["已批准", "Approved"],
  REJECTED: ["已驳回", "Rejected"],
  REQUESTED: ["待服务方响应", "Requested"],
  CONFIRMED: ["服务方已确认", "Confirmed"],
  CHANGE_PENDING: ["待重排确认", "Rescheduling required"],
  IN_PROGRESS: ["作业中", "In progress"],
  REPORTED: ["待独立验收", "Reported"],
  ACCEPTED: ["已验收", "Accepted"],
  DECLINED: ["服务方已拒绝", "Declined"],
  DISPUTED: ["验收异议", "Disputed"],
};
/** 业务按钮遵守角色、机构绑定、双人规则及当前状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(type, r, me, call) {
  if (!r || !me) return [];
  const staff = !me.agencyId,
    agent = staff || me.agencyKind === "AGENT",
    provider = me.agencyKind === "PROVIDER" && me.agencyId === r.providerId;
  let a = [];
  const different = (...ids) => ids.every((x) => x !== me.id);
  if (type === "calls") {
    if (r.status === "DRAFT" && agent)
      a = [
        ["submit", "call.write"],
        ["cancel", "call.write"],
      ];
    if (r.status === "SUBMITTED")
      a = [
        ...(agent ? [["cancel", "call.write"]] : []),
        ...(staff && different(r.createdBy)
          ? [
              ["approve", "call.approve"],
              ["reject", "call.approve"],
            ]
          : []),
      ];
    if (r.status === "PLANNED")
      a = [
        ...(agent ? [["cancel", "call.write"]] : []),
        ...(staff ? [["arrive", "call.event"]] : []),
      ];
    if (r.status === "ARRIVED" && staff) a = [["depart", "call.event"]];
    if (r.status === "DEPARTED" && staff && different(r.createdBy))
      a = [["close", "call.close"]];
  } else if (type === "changes") {
    if (r.status === "DRAFT" && agent)
      a = [
        ...(call?.status === "PLANNED" ? [["submit", "change.write"]] : []),
        ["cancel", "change.write"],
      ];
    if (r.status === "SUBMITTED" && agent)
      a = [
        ["cancel", "change.write"],
        ...(staff && different(r.createdBy)
          ? [
              ...(call?.status === "PLANNED"
                ? [["approve", "change.review"]]
                : []),
              ["reject", "change.review"],
            ]
          : []),
      ];
  } else if (
    type === "services" &&
    call &&
    !["CLOSED", "CANCELLED"].includes(call.status)
  ) {
    if (
      staff &&
      [
        "DRAFT",
        "REQUESTED",
        "CONFIRMED",
        "CHANGE_PENDING",
        "DECLINED",
      ].includes(r.status)
    )
      a.push(["cancel", "service.write"]);
    if (["PLANNED", "ARRIVED"].includes(call.status)) {
      if (staff && r.status === "DRAFT") a.push(["request", "service.write"]);
      if (staff && r.status === "CHANGE_PENDING")
        a.push(["replan", "service.write"]);
      if (
        provider &&
        r.status === "REQUESTED" &&
        r.planRevision === call.planRevision
      )
        a.push(["confirm", "service.respond"], ["decline", "service.respond"]);
    }
    if (
      provider &&
      r.status === "CONFIRMED" &&
      call.status === "ARRIVED" &&
      r.planRevision === call.planRevision
    )
      a.push(["start", "service.perform"]);
    if (
      provider &&
      ["IN_PROGRESS", "DISPUTED"].includes(r.status) &&
      ["ARRIVED", "DEPARTED"].includes(call.status)
    )
      a.push(["report", "service.perform"]);
    if (
      staff &&
      r.status === "REPORTED" &&
      different(r.createdBy, r.reportedBy)
    )
      a.push(["accept", "service.review"], ["dispute", "service.review"]);
  }
  return a.filter(([, p]) => me.permissions.includes(p)).map(([x]) => x);
}
/** 上海时区本地表单转换为明确UTC时间，避免浏览器时区歧义。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, fields) {
  return Object.fromEntries(
    fields.map(([k, , , type]) => [
      k,
      type === "integer" || type === "id"
        ? form[k] === "" || form[k] == null
          ? null
          : Number(form[k])
        : type === "boolean"
          ? Boolean(form[k])
          : type === "permissions"
            ? [...(form[k] || [])]
            : type === "datetime"
              ? form[k]
                ? new Date(form[k] + "+08:00").toISOString()
                : null
              : (form[k] ?? ""),
    ]),
  );
}
/** 以固定业务时区还原数据库时间，供日期表单编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localTime(value) {
  if (!value) return "";
  const p = Object.fromEntries(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: "Asia/Shanghai",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hourCycle: "h23",
    })
      .formatToParts(new Date(value))
      .map((x) => [x.type, x.value]),
  );
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}`;
}
