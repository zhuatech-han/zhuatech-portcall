// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP/JPA完整挂靠、变更、服务与机构范围的事务检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Import(CallIntegrationTest.FixedTime.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CallIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();
  static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z"),
      ETA = NOW.minusSeconds(7200),
      ETD = NOW.plusSeconds(86400),
      ARRIVAL = NOW.minusSeconds(6000),
      START = NOW.minusSeconds(5000),
      END = NOW.minusSeconds(4000),
      DEPART = NOW.minusSeconds(3000);

  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("portcall.admin-password", () -> password);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate sql;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, ops, review, agent, provider, otherProvider, boundAll, outside;
  long agentId, providerId, otherId, shipId;
  Map<String, Long> roles = new HashMap<>();
  String suffix;
  JsonNode call;

  String key() {
    return UUID.randomUUID().toString();
  }

  MockHttpSession login(String name) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  MvcResult request(MockHttpSession who, String method, String path, Object body) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    b.session(who).with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object body) throws Exception {
    var r = request(who, method, path, body);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object body, int status, String code)
      throws Exception {
    var r = request(who, method, path, body);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  MockHttpSession user(String name, String role, long department, Long agency) throws Exception {
    var b =
        new HashMap<String, Object>(
            Map.of(
                "username",
                name + suffix,
                "displayName",
                "TEST " + name,
                "password",
                password,
                "roleId",
                roles.get(role),
                "departmentId",
                department,
                "enabled",
                true));
    if (agency != null) b.put("agencyId", agency);
    ok(admin, "POST", "/admin/users", b);
    return login(name + suffix);
  }

  Map<String, Object> master(String kind, long department) {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "reference",
            "TEST-" + key(),
            "name",
            "TEST " + kind,
            "kind",
            kind,
            "departmentId",
            department,
            "enabled",
            true));
  }

  @BeforeAll
  void init() throws Exception {
    suffix = key().substring(0, 8);
    admin = login("admin");
    for (var r : ok(admin, "GET", "/admin/roles", null))
      roles.put(r.path("name").asString(), r.path("id").asLong());
    ops = user("ops", "挂靠协调", 1, null);
    review = user("review", "独立复核", 1, null);
    agentId = ok(ops, "POST", "/agencies", master("AGENT", 1)).path("id").asLong();
    providerId = ok(ops, "POST", "/agencies", master("PROVIDER", 1)).path("id").asLong();
    otherId = ok(ops, "POST", "/agencies", master("PROVIDER", 1)).path("id").asLong();
    shipId = ok(ops, "POST", "/vessels", master("VESSEL", 1)).path("id").asLong();
    agent = user("agent", "船舶代理", 1, agentId);
    provider = user("provider", "服务提供方", 1, providerId);
    otherProvider = user("other", "服务提供方", 1, otherId);
    boundAll = user("bound", "管理员", 1, providerId);
    long dep =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST Outside " + key()))
            .path("id")
            .asLong();
    outside = user("outside", "挂靠协调", dep, null);
  }

  @BeforeEach
  void fresh() throws Exception {
    call = newCall(agent);
  }

  JsonNode newCall(MockHttpSession creator) throws Exception {
    return ok(
            creator,
            "POST",
            "/calls",
            new HashMap<>(
                Map.of(
                    "requestKey",
                    key(),
                    "reference",
                    "TEST-CALL-" + key(),
                    "vesselId",
                    shipId,
                    "agentId",
                    agentId,
                    "location",
                    "TEST berth A",
                    "eta",
                    ETA,
                    "etd",
                    ETD)))
        .path("record");
  }

  Map<String, Object> command(JsonNode row) {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "version",
            row.path("version").asLong(),
            "note",
            "TEST verified evidence"));
  }

  JsonNode act(
      MockHttpSession who, String type, JsonNode row, String action, Map<String, Object> extra)
      throws Exception {
    var b = command(row);
    b.putAll(extra);
    return ok(who, "POST", "/" + type + "/" + row.path("id").asLong() + "/commands/" + action, b)
        .path("record");
  }

  JsonNode act(MockHttpSession who, String type, JsonNode row, String action) throws Exception {
    return act(who, type, row, action, Map.of());
  }

  JsonNode planned() throws Exception {
    call = act(agent, "calls", call, "submit");
    call = act(review, "calls", call, "approve");
    return call;
  }

  JsonNode service(long providerId, boolean critical) throws Exception {
    return ok(
            ops,
            "POST",
            "/services",
            Map.of(
                "requestKey",
                key(),
                "reference",
                "TEST-SERVICE-" + key(),
                "callId",
                call.path("id").asLong(),
                "providerId",
                providerId,
                "kind",
                "CARGO",
                "critical",
                critical,
                "windowStart",
                ETA.plusSeconds(1000),
                "windowEnd",
                ETA.plusSeconds(5000)))
        .path("record");
  }

  JsonNode confirmed() throws Exception {
    var s = service(providerId, true);
    s = act(ops, "services", s, "request");
    return act(provider, "services", s, "confirm");
  }

  JsonNode change() throws Exception {
    return ok(
            agent,
            "POST",
            "/changes",
            Map.of(
                "requestKey",
                key(),
                "reference",
                "TEST-CHANGE-" + key(),
                "callId",
                call.path("id").asLong(),
                "baseRevision",
                call.path("planRevision").asLong(),
                "eta",
                ETA.plusSeconds(3600),
                "etd",
                ETD.plusSeconds(3600)))
        .path("record");
  }

  JsonNode current(String type, long id) throws Exception {
    return ok(admin, "GET", "/" + type + "/" + id, null).path("record");
  }

  @Test
  void completeIndependentCallAndService() throws Exception {
    planned();
    var s = confirmed();
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    s = act(provider, "services", s, "start", Map.of("at", START));
    s = act(provider, "services", s, "report", Map.of("at", END));
    s = act(review, "services", s, "accept");
    call = act(ops, "calls", call, "depart", Map.of("at", DEPART));
    call = act(review, "calls", call, "close");
    assertEquals("CLOSED", call.path("status").asString());
    assertEquals("ACCEPTED", s.path("status").asString());
    assertEquals(
        6, ok(agent, "GET", "/calls/" + call.path("id").asLong(), null).path("events").size());
  }

  @Test
  void changedPlanInvalidatesConfirmationAndRequiresNewResponse() throws Exception {
    planned();
    var s = confirmed();
    var x = act(agent, "changes", change(), "submit");
    x = act(review, "changes", x, "approve");
    call = current("calls", call.path("id").asLong());
    assertEquals(2, call.path("planRevision").asLong());
    s = current("services", s.path("id").asLong());
    assertEquals("CHANGE_PENDING", s.path("status").asString());
    fail(
        provider,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/confirm",
        command(s),
        409,
        "INVALID_STATE");
    s =
        act(
            ops,
            "services",
            s,
            "replan",
            Map.of("windowStart", ETA.plusSeconds(4000), "windowEnd", ETA.plusSeconds(6000)));
    s = act(provider, "services", s, "confirm");
    assertEquals(2, s.path("planRevision").asLong());
    assertEquals("CONFIRMED", s.path("status").asString());
  }

  @Test
  void cannotSelfApproveCallOrChange() throws Exception {
    call = act(admin, "calls", newCall(admin), "submit");
    fail(
        admin,
        "POST",
        "/calls/" + call.path("id").asLong() + "/commands/approve",
        command(call),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
    call = act(review, "calls", call, "approve");
    var x =
        ok(
                admin,
                "POST",
                "/changes",
                Map.of(
                    "requestKey",
                    key(),
                    "reference",
                    "TEST-" + key(),
                    "callId",
                    call.path("id").asLong(),
                    "baseRevision",
                    1,
                    "eta",
                    ETA.plusSeconds(1),
                    "etd",
                    ETD.plusSeconds(1)))
            .path("record");
    x = act(admin, "changes", x, "submit");
    fail(
        admin,
        "POST",
        "/changes/" + x.path("id").asLong() + "/commands/approve",
        command(x),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void onlyBoundProviderCanRespondOrPerform() throws Exception {
    planned();
    var s = act(ops, "services", service(providerId, true), "request");
    String p = "/services/" + s.path("id").asLong() + "/commands/confirm";
    fail(admin, "POST", p, command(s), 403, "BOUND_PROVIDER_REQUIRED");
    fail(otherProvider, "POST", p, command(s), 403, "OUT_OF_SCOPE");
    s = act(provider, "services", s, "confirm");
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    fail(
        admin,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/start",
        new HashMap<>(
            Map.of(
                "requestKey",
                key(),
                "version",
                s.path("version").asLong(),
                "note",
                "TEST",
                "at",
                START)),
        403,
        "BOUND_PROVIDER_REQUIRED");
  }

  @Test
  void providerAllScopeCannotSeeOtherOrdersOrManagement() throws Exception {
    planned();
    var s = confirmed();
    var other = service(otherId, false);
    var d = ok(boundAll, "GET", "/calls/" + call.path("id").asLong(), null);
    assertEquals(1, d.path("services").size());
    assertFalse(d.has("changes"));
    assertEquals(s.path("id").asLong(), d.path("services").get(0).path("id").asLong());
    fail(boundAll, "GET", "/services/" + other.path("id").asLong(), null, 403, "OUT_OF_SCOPE");
    fail(boundAll, "GET", "/admin/users", null, 403, "STAFF_ONLY");
    fail(boundAll, "GET", "/changes", null, 403, "AGENT_ONLY");
    fail(boundAll, "POST", "/calls", Map.of(), 403, "AGENT_ONLY");
  }

  @Test
  void outsiderAndOtherAgentCannotReadOrExport() throws Exception {
    planned();
    var extra = ok(ops, "POST", "/agencies", master("AGENT", 1)).path("id").asLong();
    var who = user("isolated" + key().substring(0, 5), "船舶代理", 1, extra);
    String p = "/calls/" + call.path("id").asLong();
    fail(who, "GET", p, null, 403, "OUT_OF_SCOPE");
    fail(outside, "GET", p, null, 403, "OUT_OF_SCOPE");
    fail(who, "GET", p + "/report.json", null, 403, "OUT_OF_SCOPE");
    assertEquals(
        0,
        ok(who, "GET", "/calls?search=" + call.path("reference").asString(), null)
            .path("total")
            .asLong());
  }

  @Test
  void staleVersionAndExactRetryDoNotDuplicateEvents() throws Exception {
    var b = command(call);
    String p = "/calls/" + call.path("id").asLong() + "/commands/submit";
    call = ok(agent, "POST", p, b).path("record");
    assertEquals(call, ok(agent, "POST", p, b).path("record"));
    assertEquals(
        2, ok(agent, "GET", "/calls/" + call.path("id").asLong(), null).path("events").size());
    var changed = new HashMap<>(b);
    changed.put("note", "TEST altered");
    fail(agent, "POST", p, changed, 409, "REQUEST_KEY_REUSED");
    fail(
        agent,
        "POST",
        p,
        new HashMap<>(Map.of("requestKey", key(), "version", 1, "note", "TEST")),
        409,
        "STALE_VERSION");
  }

  @Test
  void sameUuidDifferentActorRejected() throws Exception {
    var b = command(call);
    String p = "/calls/" + call.path("id").asLong() + "/commands/submit";
    ok(agent, "POST", p, b);
    fail(admin, "POST", p, b, 409, "REQUEST_KEY_REUSED");
  }

  @Test
  void failedCommandRollsBackAndDoesNotConsumeUuid() throws Exception {
    planned();
    var b = command(call);
    b.put("at", NOW.plusSeconds(1));
    String p = "/calls/" + call.path("id").asLong() + "/commands/arrive";
    fail(ops, "POST", p, b, 400, "INVALID_ACTUAL_TIME");
    assertEquals(call, current("calls", call.path("id").asLong()));
    b.put("at", ARRIVAL);
    call = ok(ops, "POST", p, b).path("record");
    assertEquals("ARRIVED", call.path("status").asString());
  }

  @Test
  void windowAndActualChronologyEnforced() throws Exception {
    planned();
    var b =
        Map.of(
            "requestKey",
            key(),
            "reference",
            "TEST-" + key(),
            "callId",
            call.path("id").asLong(),
            "providerId",
            providerId,
            "kind",
            "CARGO",
            "critical",
            true,
            "windowStart",
            ETA.minusSeconds(1),
            "windowEnd",
            ETD);
    fail(ops, "POST", "/services", b, 400, "OUTSIDE_PLAN");
    var s = confirmed();
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    var c = command(s);
    c.put("at", ARRIVAL.minusSeconds(1));
    fail(
        provider,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/start",
        c,
        400,
        "INVALID_ACTUAL_TIME");
    s = act(provider, "services", s, "start", Map.of("at", START));
    var report = command(s);
    report.put("at", START.minusSeconds(1));
    fail(
        provider,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/report",
        report,
        400,
        "INVALID_ACTUAL_TIME");
  }

  @Test
  void actualDepartureRetainsUnresolvedServiceAndBlocksClose() throws Exception {
    planned();
    var s = confirmed();
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    s = act(provider, "services", s, "start", Map.of("at", START));
    call = act(ops, "calls", call, "depart", Map.of("at", DEPART));
    fail(
        review,
        "POST",
        "/calls/" + call.path("id").asLong() + "/commands/close",
        command(call),
        409,
        "UNRESOLVED_WORK");
    s = act(provider, "services", s, "report", Map.of("at", END));
    s = act(review, "services", s, "accept");
    call = act(review, "calls", call, "close");
    assertEquals("CLOSED", call.path("status").asString());
  }

  @Test
  void cannotReportAfterAlreadyRecordedDeparture() throws Exception {
    planned();
    var s = confirmed();
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    s = act(provider, "services", s, "start", Map.of("at", START));
    call = act(ops, "calls", call, "depart", Map.of("at", DEPART));
    var b = command(s);
    b.put("at", DEPART.plusSeconds(1));
    fail(
        provider,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/report",
        b,
        400,
        "AFTER_DEPARTURE");
    assertTrue(current("services", s.path("id").asLong()).path("actualEnd").isNull());
  }

  @Test
  void disputedEvidenceCanBeCorrectedThenIndependentlyAccepted() throws Exception {
    planned();
    var s = confirmed();
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    s = act(provider, "services", s, "start", Map.of("at", START));
    s = act(provider, "services", s, "report", Map.of("at", END));
    s = act(review, "services", s, "dispute");
    s = act(provider, "services", s, "report", Map.of("at", END.plusSeconds(1)));
    s = act(review, "services", s, "accept");
    assertEquals("ACCEPTED", s.path("status").asString());
    assertEquals(
        8, ok(provider, "GET", "/services/" + s.path("id").asLong(), null).path("events").size());
  }

  @Test
  void serviceRequesterCannotAcceptOwnWorkRequest() throws Exception {
    planned();
    var s =
        ok(
                admin,
                "POST",
                "/services",
                Map.of(
                    "requestKey",
                    key(),
                    "reference",
                    "TEST-" + key(),
                    "callId",
                    call.path("id").asLong(),
                    "providerId",
                    providerId,
                    "kind",
                    "CARGO",
                    "critical",
                    true,
                    "windowStart",
                    ETA.plusSeconds(1),
                    "windowEnd",
                    ETD))
            .path("record");
    s = act(admin, "services", s, "request");
    s = act(provider, "services", s, "confirm");
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    s = act(provider, "services", s, "start", Map.of("at", START));
    s = act(provider, "services", s, "report", Map.of("at", END));
    fail(
        admin,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/accept",
        command(s),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void plansCannotChangeAfterActualArrival() throws Exception {
    planned();
    var x = act(agent, "changes", change(), "submit");
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    fail(
        review,
        "POST",
        "/changes/" + x.path("id").asLong() + "/commands/approve",
        command(x),
        409,
        "INVALID_STATE");
    x = act(review, "changes", x, "reject");
    assertEquals("REJECTED", x.path("status").asString());
  }

  @Test
  void duplicateOpenChangeAndStaleBaselineRejected() throws Exception {
    planned();
    change();
    fail(
        agent,
        "POST",
        "/changes",
        Map.of(
            "requestKey",
            key(),
            "reference",
            "TEST-" + key(),
            "callId",
            call.path("id").asLong(),
            "baseRevision",
            1,
            "eta",
            ETA.plusSeconds(1),
            "etd",
            ETD.plusSeconds(1)),
        409,
        "OPEN_CHANGE");
    fail(
        agent,
        "POST",
        "/changes",
        Map.of(
            "requestKey",
            key(),
            "reference",
            "TEST-" + key(),
            "callId",
            call.path("id").asLong(),
            "baseRevision",
            2,
            "eta",
            ETA.plusSeconds(1),
            "etd",
            ETD.plusSeconds(1)),
        409,
        "STALE_PLAN");
  }

  @Test
  void concurrentApprovalsApplyPlanOnlyOnce() throws Exception {
    planned();
    var x = act(agent, "changes", change(), "submit");
    String path = "/changes/" + x.path("id").asLong() + "/commands/approve";
    var a = command(x);
    var b = command(x);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<Integer> first =
          () -> {
            gate.await();
            return request(review, "POST", path, a).getResponse().getStatus();
          };
      Callable<Integer> second =
          () -> {
            gate.await();
            return request(review, "POST", path, b).getResponse().getStatus();
          };
      var one = pool.submit(first);
      var two = pool.submit(second);
      gate.countDown();
      assertEquals(Set.of(200, 409), Set.of(one.get(), two.get()));
    }
    assertEquals(2, current("calls", call.path("id").asLong()).path("planRevision").asLong());
  }

  @Test
  void cancelBeforeArrivalCascadesRequestsAndChangesWithEvidence() throws Exception {
    planned();
    var s = confirmed();
    var x = change();
    call = act(agent, "calls", call, "cancel");
    assertEquals("CANCELLED", current("services", s.path("id").asLong()).path("status").asString());
    assertEquals("CANCELLED", current("changes", x.path("id").asLong()).path("status").asString());
    assertEquals("CANCELLED", call.path("status").asString());
  }

  @Test
  void terminalServiceAndActualCallCannotBeCancelledOrEdited() throws Exception {
    planned();
    var s = confirmed();
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    fail(
        agent,
        "POST",
        "/calls/" + call.path("id").asLong() + "/commands/cancel",
        command(call),
        409,
        "INVALID_STATE");
    s = act(provider, "services", s, "start", Map.of("at", START));
    fail(
        ops,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/cancel",
        command(s),
        409,
        "INVALID_STATE");
    s = act(provider, "services", s, "report", Map.of("at", END));
    s = act(review, "services", s, "accept");
    fail(
        ops,
        "POST",
        "/services/" + s.path("id").asLong() + "/commands/cancel",
        command(s),
        409,
        "INVALID_STATE");
  }

  @Test
  void rejectedCriticalServiceBlocksCloseUntilReplacementExists() throws Exception {
    planned();
    var s = act(ops, "services", service(providerId, true), "request");
    s = act(provider, "services", s, "decline");
    call = act(ops, "calls", call, "arrive", Map.of("at", ARRIVAL));
    call = act(ops, "calls", call, "depart", Map.of("at", DEPART));
    fail(
        review,
        "POST",
        "/calls/" + call.path("id").asLong() + "/commands/close",
        command(call),
        409,
        "UNRESOLVED_WORK");
    s = act(ops, "services", s, "cancel");
    fail(
        review,
        "POST",
        "/calls/" + call.path("id").asLong() + "/commands/close",
        command(call),
        409,
        "UNRESOLVED_WORK");
  }

  @Test
  void mastersHaveForeignKeyProtectionAndUnreferencedDeletion() throws Exception {
    fail(ops, "DELETE", "/agencies/" + agentId, null, 409, "CONFLICT");
    fail(ops, "DELETE", "/vessels/" + shipId, null, 409, "CONFLICT");
    var x = ok(ops, "POST", "/agencies", master("PROVIDER", 1));
    ok(ops, "DELETE", "/agencies/" + x.path("id").asLong(), null);
    assertEquals(
        0,
        sql.queryForObject(
            "select count(*) from agency where id=?", Integer.class, x.path("id").asLong()));
  }

  @Test
  void lastStaffAdminProtectedEvenIfBoundAllAccountExists() throws Exception {
    var me = ok(admin, "GET", "/auth/me", null);
    fail(
        admin,
        "PUT",
        "/admin/users/" + me.path("id").asLong(),
        Map.of(
            "username",
            "admin",
            "displayName",
            "TEST Admin",
            "roleId",
            roles.get("管理员"),
            "departmentId",
            1,
            "enabled",
            false),
        409,
        "LAST_ADMIN");
    assertEquals(200, request(admin, "GET", "/auth/me", null).getResponse().getStatus());
  }

  @Test
  void anonymousAndMissingCsrfWritesRejected() throws Exception {
    assertEquals(401, mvc.perform(get("/api/calls")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/calls/" + call.path("id").asLong() + "/commands/submit")
                    .session(agent)
                    .contentType("application/json")
                    .content(json.writeValueAsString(command(call))))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void disabledAccountInvalidatedWithoutNewLogin() throws Exception {
    var extra = user("revoke" + key().substring(0, 5), "挂靠协调", 1, null);
    var me = ok(extra, "GET", "/auth/me", null);
    ok(
        admin,
        "PUT",
        "/admin/users/" + me.path("id").asLong(),
        Map.of(
            "username",
            me.path("username").asString(),
            "displayName",
            "TEST Revoke",
            "roleId",
            roles.get("挂靠协调"),
            "departmentId",
            1,
            "enabled",
            false));
    fail(extra, "GET", "/auth/me", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void jsonExportUsesSameProviderScopeAndHasNoAdvertising() throws Exception {
    planned();
    var s = confirmed();
    service(otherId, false);
    String path = "/calls/" + call.path("id").asLong();
    assertEquals(ok(provider, "GET", path, null), ok(provider, "GET", path + "/report.json", null));
    var r = request(provider, "GET", path + "/report.json", null);
    assertEquals("no-store", r.getResponse().getHeader("Cache-Control"));
    assertFalse(r.getResponse().getContentAsString().contains("zhuatech2"));
    assertEquals(1, json.readTree(r.getResponse().getContentAsString()).path("services").size());
    assertTrue(s.path("critical").asBoolean());
  }

  @Test
  void allOldPasswordSessionsAreInvalidated() throws Exception {
    String n = "password" + key().substring(0, 5);
    var who = user(n, "挂靠协调", 1, null);
    var second = login(n + suffix);
    ok(
        who,
        "POST",
        "/auth/password",
        Map.of("oldPassword", password, "newPassword", "Bb4" + key()));
    fail(second, "GET", "/auth/me", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void selfScopeReadsOnlyOwnCreatedCalls() throws Exception {
    var original = ok(admin, "GET", "/admin/roles", null);
    JsonNode rr = null;
    for (var r : original) if (r.path("name").asString().equals("挂靠协调")) rr = r;
    var clone =
        ok(
            admin,
            "POST",
            "/admin/roles",
            Map.of(
                "name",
                "TEST SELF " + key(),
                "scope",
                "SELF",
                "permissions",
                json.convertValue(rr.path("permissions"), List.class)));
    roles.put("testself", clone.path("id").asLong());
    var self = user("self" + key().substring(0, 5), "testself", 1, null);
    var own = newCall(self);
    assertEquals(
        1,
        ok(self, "GET", "/calls?search=" + own.path("reference").asString(), null)
            .path("total")
            .asLong());
    fail(self, "GET", "/calls/" + call.path("id").asLong(), null, 403, "OUT_OF_SCOPE");
  }
}
