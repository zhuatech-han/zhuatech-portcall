// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { actions, payload, localTime } from "./domain.js";
import { commandFields } from "./forms.js";
const all = [
  "call.write",
  "call.approve",
  "call.event",
  "call.close",
  "change.write",
  "change.review",
  "service.write",
  "service.respond",
  "service.perform",
  "service.review",
];
const staff = { id: 1, agencyId: null, permissions: all };
const provider = {
  id: 2,
  agencyId: 9,
  agencyKind: "PROVIDER",
  permissions: all,
};
/** 独立审批与关单由实际业务岗位控制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
test("hides own approval and close", () => {
  assert.deepEqual(
    actions("calls", { status: "SUBMITTED", createdBy: 1 }, staff),
    ["cancel"],
  );
  assert.deepEqual(
    actions("calls", { status: "DEPARTED", createdBy: 1 }, staff),
    [],
  );
});
test("requires actual bound provider for confirmation", () => {
  const r = { status: "REQUESTED", providerId: 9, planRevision: 2 };
  const c = { status: "PLANNED", planRevision: 2 };
  assert.deepEqual(actions("services", r, staff, c), ["cancel"]);
  assert.deepEqual(actions("services", r, provider, c), ["confirm", "decline"]);
  assert.deepEqual(
    actions("services", r, { ...provider, agencyId: 10 }, c),
    [],
  );
});
test("requires replan after plan changes", () => {
  assert.deepEqual(
    actions(
      "services",
      { status: "CHANGE_PENDING", providerId: 9, planRevision: 1 },
      provider,
      { status: "PLANNED", planRevision: 2 },
    ),
    [],
  );
  assert.deepEqual(
    actions("services", { status: "CHANGE_PENDING" }, staff, {
      status: "PLANNED",
    }),
    ["cancel", "replan"],
  );
  assert.deepEqual(
    actions(
      "services",
      { status: "REQUESTED", providerId: 9, planRevision: 1 },
      provider,
      { status: "PLANNED", planRevision: 2 },
    ),
    [],
  );
});
test("retains report after departure and hides starting", () => {
  assert.deepEqual(
    actions("services", { status: "IN_PROGRESS", providerId: 9 }, provider, {
      status: "DEPARTED",
    }),
    ["report"],
  );
  assert.deepEqual(
    actions(
      "services",
      { status: "CONFIRMED", providerId: 9, planRevision: 1 },
      provider,
      { status: "DEPARTED", planRevision: 1 },
    ),
    [],
  );
});
test("requires independent acceptance and actual permissions", () => {
  assert.deepEqual(
    actions(
      "services",
      { status: "REPORTED", createdBy: 1, reportedBy: 2 },
      staff,
      { status: "ARRIVED" },
    ),
    [],
  );
  assert.deepEqual(
    actions("calls", { status: "PLANNED" }, { ...staff, permissions: [] }),
    [],
  );
});
test("converts Shanghai form times independent of browser timezone", () => {
  assert.equal(localTime("2026-10-06T00:00:00Z"), "2026-10-06T08:00:00");
  assert.equal(
    payload({ at: "2026-10-06T08:00:00" }, commandFields("arrive")).at,
    "2026-10-06T00:00:00.000Z",
  );
  assert.equal(payload({ at: "" }, commandFields("arrive")).at, null);
});
test("collects actual milestones and new windows with evidence", () => {
  assert.deepEqual(
    commandFields("report").map((x) => x[0]),
    ["at", "note"],
  );
  assert.deepEqual(
    commandFields("replan").map((x) => x[0]),
    ["windowStart", "windowEnd", "note"],
  );
});
