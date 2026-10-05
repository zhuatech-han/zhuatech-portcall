// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 挂靠、计划变更和服务请求的事务边界；计划确认与实际事实分别持久化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CallService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper mapper;

  public CallService(Store db, AccessService access, Clock clock, ObjectMapper mapper) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.mapper = mapper;
  }

  /** 船舶／机构目录的有限输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record MasterInput(
      String requestKey,
      Long version,
      String reference,
      String name,
      String kind,
      Long departmentId,
      Boolean enabled) {}

  /** 挂靠计划草稿，只接受预计时间，不可伪造实际事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CallInput(
      String requestKey,
      Long version,
      String reference,
      Long vesselId,
      Long agentId,
      String location,
      Instant eta,
      Instant etd) {}

  /** 变更绑定明确基线，不能覆盖刚批准的新计划。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ChangeInput(
      String requestKey,
      Long version,
      String reference,
      Long callId,
      Long baseRevision,
      Instant eta,
      Instant etd) {}

  /** 服务方、类型、关键性和窗口在发送后冻结。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ServiceInput(
      String requestKey,
      Long version,
      String reference,
      Long callId,
      Long providerId,
      String kind,
      Boolean critical,
      Instant windowStart,
      Instant windowEnd) {}

  /** 完整命令载荷绑定UUID、版本、证据和人工核实时刻。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      String requestKey,
      Long version,
      String note,
      Instant at,
      Instant windowStart,
      Instant windowEnd) {}

  private static final Set<String> REPLAN =
      Set.of("DRAFT", "REQUESTED", "CONFIRMED", "CHANGE_PENDING");
  private static final Set<String> FINAL_SERVICE = Set.of("ACCEPTED", "DECLINED", "CANCELLED");

  private void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  /** 机构绑定优先于ALL，所有内部管理接口限制内部账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void staff() {
    if (access.current().agencyId != null) throw new Problem(403, "STAFF_ONLY");
  }

  private Agency binding() {
    var a = access.current();
    return a.agencyId == null ? null : db.get(Agency.class, a.agencyId);
  }

  private void agentOrStaff() {
    var b = binding();
    if (b != null && !b.kind.equals("AGENT")) throw new Problem(403, "AGENT_ONLY");
  }

  private void provider(ServiceOrder s) {
    var a = access.current();
    var b = binding();
    if (b == null || !b.kind.equals("PROVIDER") || !Objects.equals(a.agencyId, s.providerId))
      throw new Problem(403, "BOUND_PROVIDER_REQUIRED");
  }

  private boolean internalVisible(Long department, Long creator) {
    return access.visible(department)
        && (!access.role().scope.equals("SELF") || Objects.equals(access.current().id, creator));
  }

  private boolean visible(PortCall c) {
    var a = access.current();
    var b = binding();
    if (b == null) return internalVisible(c.departmentId, c.createdBy);
    if (!Objects.equals(a.departmentId, c.departmentId)
        || !Objects.equals(b.departmentId, c.departmentId)) return false;
    return b.kind.equals("AGENT")
        ? Objects.equals(b.id, c.agentId)
        : db.query(
                    ServiceOrder.class,
                    "from ServiceOrder where callId=?1 and providerId=?2",
                    c.id,
                    b.id)
                .size()
            > 0;
  }

  private boolean serviceVisible(ServiceOrder s) {
    var b = binding();
    return visible(db.get(PortCall.class, s.callId))
        && (b == null || !b.kind.equals("PROVIDER") || Objects.equals(b.id, s.providerId));
  }

  private PortCall call(Long id) {
    var c = db.get(PortCall.class, id);
    if (!visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
    return c;
  }

  private PlanChange change(Long id) {
    agentOrStaff();
    var c = db.get(PlanChange.class, id);
    call(c.callId);
    return c;
  }

  private ServiceOrder service(Long id) {
    var s = db.get(ServiceOrder.class, id);
    if (!serviceVisible(s)) throw new Problem(403, "OUT_OF_SCOPE");
    return s;
  }

  private Object vesselSummary(Vessel v) {
    return Map.of(
        "id",
        v.id,
        "reference",
        v.reference,
        "name",
        v.name,
        "departmentId",
        v.departmentId,
        "enabled",
        v.enabled);
  }

  private Object agencySummary(Agency a) {
    return Map.of(
        "id",
        a.id,
        "reference",
        a.reference,
        "name",
        a.name,
        "kind",
        a.kind,
        "departmentId",
        a.departmentId,
        "enabled",
        a.enabled);
  }

  /** 表单范围：服务商只见自己关联挂靠，船代只见本机构和本部门船舶基本信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    var a = access.current();
    var b = binding();
    var calls = db.all(PortCall.class).stream().filter(this::visible).toList();
    return Map.of(
        "calls",
        calls,
        "vessels",
        db.all(Vessel.class).stream()
            .filter(
                v ->
                    b == null
                        ? internalVisible(v.departmentId, v.createdBy)
                        : b.kind.equals("AGENT")
                            ? Objects.equals(a.departmentId, v.departmentId)
                            : calls.stream().anyMatch(c -> Objects.equals(c.vesselId, v.id)))
            .map(this::vesselSummary)
            .toList(),
        "agencies",
        db.all(Agency.class).stream()
            .filter(
                v ->
                    b == null
                        ? internalVisible(v.departmentId, v.createdBy)
                        : Objects.equals(b.id, v.id))
            .map(this::agencySummary)
            .toList(),
        "departments",
        b == null
            ? db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList()
            : List.of(),
        "serviceTypes",
        db.all(DictionaryEntry.class).stream().filter(x -> x.type.equals("service")).toList(),
        "companyName",
        setting("companyName"),
        "timezone",
        setting("timezone"));
  }

  /** 目录身份固定；停用不删除引用，类型只允许船代／服务商。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveMaster(String type, Long id, MasterInput v) {
    lock();
    staff();
    access.require("catalog.write");
    String fp = fingerprint(type + "_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return master(type, prior);
    var d = db.get(Department.class, v.departmentId);
    access.department(d.id);
    var ref = text(v.reference, 80);
    Object result;
    Long resultId;
    if (type.equals("vessels")) {
      var x = id == null ? new Vessel() : (Vessel) master(type, id);
      if (id != null) {
        version(x.version, v.version);
        immutable(x.departmentId, d.id);
        immutable(x.reference, ref);
      }
      x.reference = ref;
      x.name = text(v.name, 120);
      x.departmentId = d.id;
      x.enabled = Boolean.TRUE.equals(v.enabled);
      if (id == null) {
        x.createdBy = access.current().id;
        capacity(Vessel.class);
        x.version = 1;
        db.save(x);
      } else x.version++;
      result = x;
      resultId = x.id;
    } else if (type.equals("agencies")) {
      var x = id == null ? new Agency() : (Agency) master(type, id);
      if (id != null) {
        version(x.version, v.version);
        immutable(x.departmentId, d.id);
        immutable(x.reference, ref);
        immutable(x.kind, v.kind);
      }
      x.reference = ref;
      x.name = text(v.name, 120);
      x.kind = choice(v.kind, "AGENT", "PROVIDER");
      x.departmentId = d.id;
      x.enabled = Boolean.TRUE.equals(v.enabled);
      if (id == null) {
        x.createdBy = access.current().id;
        capacity(Agency.class);
        x.version = 1;
        db.save(x);
      } else x.version++;
      result = x;
      resultId = x.id;
    } else throw new Problem(404, "NOT_FOUND");
    event(type, resultId, "SAVE", "", result, d.id);
    remember(v.requestKey, fp, resultId);
    return result;
  }

  private Object master(String type, Long id) {
    Object x = type.equals("vessels") ? db.get(Vessel.class, id) : db.get(Agency.class, id);
    var dep = x instanceof Vessel v ? v.departmentId : ((Agency) x).departmentId;
    var creator = x instanceof Vessel v ? v.createdBy : ((Agency) x).createdBy;
    if (!internalVisible(dep, creator)) throw new Problem(403, "OUT_OF_SCOPE");
    return x;
  }

  /** 只删除未引用的目录，业务历史外键始终保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteMaster(String type, Long id) {
    lock();
    staff();
    access.require("catalog.write");
    var x = master(type, id);
    var dep = x instanceof Vessel v ? v.departmentId : ((Agency) x).departmentId;
    db.delete(x);
    access.audit(type + "_DELETE", id, dep);
  }

  /** 后端范围过滤、白名单状态和有界分页，不以菜单代替权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(
      String type, String search, String status, String kind, int page, int size, String sort) {
    List<?> rows;
    switch (type) {
      case "vessels" -> {
        staff();
        access.require("catalog.write");
        rows =
            db.all(Vessel.class).stream()
                .filter(v -> internalVisible(v.departmentId, v.createdBy))
                .filter(v -> contains(search, v.reference, v.name))
                .filter(v -> status.isBlank() || Boolean.toString(v.enabled).equals(status))
                .toList();
      }
      case "agencies" -> {
        staff();
        access.require("catalog.write");
        rows =
            db.all(Agency.class).stream()
                .filter(v -> internalVisible(v.departmentId, v.createdBy))
                .filter(v -> contains(search, v.reference, v.name))
                .filter(v -> status.isBlank() || Boolean.toString(v.enabled).equals(status))
                .filter(v -> kind.isBlank() || v.kind.equals(kind))
                .toList();
      }
      case "calls" -> {
        access.require("call.read");
        rows =
            db.all(PortCall.class).stream()
                .filter(this::visible)
                .filter(
                    c ->
                        contains(
                            search, c.reference, c.location, db.get(Vessel.class, c.vesselId).name))
                .filter(c -> status.isBlank() || c.status.equals(status))
                .toList();
      }
      case "changes" -> {
        agentOrStaff();
        access.require("change.read");
        rows =
            db.all(PlanChange.class).stream()
                .filter(c -> visible(db.get(PortCall.class, c.callId)))
                .filter(c -> contains(search, c.reference))
                .filter(c -> status.isBlank() || c.status.equals(status))
                .toList();
      }
      case "services" -> {
        access.require("service.read");
        rows =
            db.all(ServiceOrder.class).stream()
                .filter(this::serviceVisible)
                .filter(
                    s -> contains(search, s.reference, db.get(PortCall.class, s.callId).reference))
                .filter(s -> status.isBlank() || s.status.equals(status))
                .filter(s -> kind.isBlank() || s.kind.equals(kind))
                .toList();
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    return page(rows, page, size, sort);
  }

  /** 可见对象及不可改写时间线；服务商的挂靠详情不返回其他机构的服务或内部变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(String type, Long id) {
    Object row;
    PortCall c;
    String eventType;
    var result = new LinkedHashMap<String, Object>();
    switch (type) {
      case "calls" -> {
        access.require("call.read");
        c = call(id);
        row = c;
        eventType = "CALL";
        result.put(
            "services",
            db
                .query(ServiceOrder.class, "from ServiceOrder where callId=?1 order by id", id)
                .stream()
                .filter(this::serviceVisible)
                .toList());
        if (binding() == null || binding().kind.equals("AGENT"))
          result.put(
              "changes",
              db.query(PlanChange.class, "from PlanChange where callId=?1 order by id", id));
      }
      case "changes" -> {
        access.require("change.read");
        var x = change(id);
        row = x;
        c = call(x.callId);
        eventType = "CHANGE";
      }
      case "services" -> {
        access.require("service.read");
        var x = service(id);
        row = x;
        c = call(x.callId);
        eventType = "SERVICE";
        result.put("provider", agencySummary(db.get(Agency.class, x.providerId)));
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    result.put("record", row);
    result.put("call", c);
    result.put("vessel", vesselSummary(db.get(Vessel.class, c.vesselId)));
    result.put("agent", agencySummary(db.get(Agency.class, c.agentId)));
    result.put(
        "events",
        db.query(
            BusinessEvent.class,
            "from BusinessEvent where objectType=?1 and objectId=?2 order by id",
            eventType,
            id));
    return result;
  }

  /** 船代只可为绑定机构制单；编号、船舶、机构和部门在已有草稿中固定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveCall(Long id, CallInput v) {
    lock();
    agentOrStaff();
    access.require("call.write");
    String fp = fingerprint("CALL_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return detail("calls", prior);
    var c = id == null ? new PortCall() : call(id);
    var ship = db.get(Vessel.class, v.vesselId);
    var agent = db.get(Agency.class, v.agentId);
    if (!agent.kind.equals("AGENT")
        || !ship.enabled
        || !agent.enabled
        || !Objects.equals(agent.departmentId, ship.departmentId))
      throw new Problem(400, "INVALID_AGENT");
    access.department(ship.departmentId);
    if (binding() != null && !Objects.equals(binding().id, agent.id))
      throw new Problem(403, "OUT_OF_SCOPE");
    var ref = text(v.reference, 80);
    if (id != null) {
      version(c.version, v.version);
      state(c.status, "DRAFT");
      immutable(c.reference, ref);
      immutable(c.vesselId, ship.id);
      immutable(c.agentId, agent.id);
    }
    c.eta = TimePolicy.time(v.eta);
    c.etd = TimePolicy.time(v.etd);
    TimePolicy.interval(c.eta, c.etd);
    c.reference = ref;
    c.vesselId = ship.id;
    c.agentId = agent.id;
    c.departmentId = ship.departmentId;
    c.location = text(v.location, 120);
    c.status = "DRAFT";
    if (id == null) {
      c.createdBy = access.current().id;
      capacity(PortCall.class);
      c.version = 1;
      db.save(c);
    } else c.version++;
    event("CALL", c.id, "SAVE", "", c, c.departmentId);
    remember(v.requestKey, fp, c.id);
    return detail("calls", c.id);
  }

  /** 到离时间记录事实，离港不隐去待验收服务；关单另需完整服务闭合和独立复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object callCommand(Long id, String action, Command v) {
    lock();
    access.require(
        switch (action) {
          case "submit", "cancel" -> "call.write";
          case "approve", "reject" -> "call.approve";
          case "arrive", "depart" -> "call.event";
          case "close" -> "call.close";
          default -> throw new Problem(400, "INVALID_ACTION");
        });
    var c = call(id);
    String fp = fingerprint("CALL_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return detail("calls", id);
    version(c.version, v.version);
    String note = text(v.note, 1000);
    switch (action) {
      case "submit" -> {
        agentOrStaff();
        state(c.status, "DRAFT");
        enabled(c);
        c.status = "SUBMITTED";
      }
      case "approve", "reject" -> {
        staff();
        state(c.status, "SUBMITTED");
        independent(c.createdBy);
        if (action.equals("approve")) {
          enabled(c);
          c.planRevision = 1;
          c.status = "PLANNED";
        } else c.status = "REJECTED";
        c.approvedBy = access.current().id;
      }
      case "cancel" -> {
        agentOrStaff();
        if (!Set.of("DRAFT", "SUBMITTED", "PLANNED").contains(c.status))
          throw new Problem(409, "INVALID_STATE");
        for (var s : db.query(ServiceOrder.class, "from ServiceOrder where callId=?1", id)) {
          if (!FINAL_SERVICE.contains(s.status)) {
            s.status = "CANCELLED";
            s.version++;
            event("SERVICE", s.id, "CALL_CANCELLED", note, s, c.departmentId);
          }
        }
        for (var x : db.query(PlanChange.class, "from PlanChange where callId=?1", id)) {
          if (Set.of("DRAFT", "SUBMITTED").contains(x.status)) {
            x.status = "CANCELLED";
            x.version++;
            event("CHANGE", x.id, "CALL_CANCELLED", note, x, c.departmentId);
          }
        }
        c.status = "CANCELLED";
      }
      case "arrive" -> {
        staff();
        state(c.status, "PLANNED");
        c.arrivedAt = TimePolicy.actual(v.at, null, clock);
        c.status = "ARRIVED";
      }
      case "depart" -> {
        staff();
        state(c.status, "ARRIVED");
        Instant last = c.arrivedAt;
        for (var s : db.query(ServiceOrder.class, "from ServiceOrder where callId=?1", id)) {
          if (s.actualStart != null && s.actualStart.isAfter(last)) last = s.actualStart;
          if (s.actualEnd != null && s.actualEnd.isAfter(last)) last = s.actualEnd;
        }
        c.departedAt = TimePolicy.actual(v.at, last, clock);
        c.status = "DEPARTED";
      }
      case "close" -> {
        staff();
        state(c.status, "DEPARTED");
        independent(c.createdBy);
        var services = db.query(ServiceOrder.class, "from ServiceOrder where callId=?1", id);
        if (services.stream().anyMatch(s -> !FINAL_SERVICE.contains(s.status))
            || services.stream()
                .anyMatch(
                    s ->
                        s.critical && !s.status.equals("CANCELLED") && !s.status.equals("ACCEPTED"))
            || services.stream().noneMatch(s -> s.critical && s.status.equals("ACCEPTED"))
            || db.query(PlanChange.class, "from PlanChange where callId=?1", id).stream()
                .anyMatch(x -> Set.of("DRAFT", "SUBMITTED").contains(x.status)))
          throw new Problem(409, "UNRESOLVED_WORK");
        c.closedBy = access.current().id;
        c.status = "CLOSED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    c.version++;
    event("CALL", id, action, note, c, c.departmentId);
    remember(v.requestKey, fp, id);
    return detail("calls", id);
  }

  private void enabled(PortCall c) {
    if (!db.get(Vessel.class, c.vesselId).enabled || !db.get(Agency.class, c.agentId).enabled)
      throw new Problem(409, "DISABLED_RESOURCE");
  }

  /** 计划变更基线不可漂移，同一挂靠最多一份开放提案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveChange(Long id, ChangeInput v) {
    lock();
    agentOrStaff();
    access.require("change.write");
    String fp = fingerprint("CHANGE_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return detail("changes", prior);
    var c = call(v.callId);
    state(c.status, "PLANNED");
    if (v.baseRevision == null || v.baseRevision != c.planRevision)
      throw new Problem(409, "STALE_PLAN");
    var x = id == null ? new PlanChange() : change(id);
    if (id != null) {
      version(x.version, v.version);
      state(x.status, "DRAFT");
      immutable(x.callId, c.id);
      immutable(x.reference, v.reference);
    }
    if (db.query(PlanChange.class, "from PlanChange where callId=?1", c.id).stream()
        .anyMatch(
            p -> !Objects.equals(p.id, id) && Set.of("DRAFT", "SUBMITTED").contains(p.status)))
      throw new Problem(409, "OPEN_CHANGE");
    x.reference = text(v.reference, 80);
    x.callId = c.id;
    x.eta = TimePolicy.time(v.eta);
    x.etd = TimePolicy.time(v.etd);
    TimePolicy.interval(x.eta, x.etd);
    if (x.eta.equals(c.eta) && x.etd.equals(c.etd)) throw new Problem(400, "UNCHANGED_PLAN");
    x.baseRevision = c.planRevision;
    x.status = "DRAFT";
    if (id == null) {
      x.createdBy = access.current().id;
      capacity(PlanChange.class);
      x.version = 1;
      db.save(x);
    } else x.version++;
    event("CHANGE", x.id, "SAVE", "", x, c.departmentId);
    remember(v.requestKey, fp, x.id);
    return detail("changes", x.id);
  }

  /** 独立批准一次推进计划版本，所有未执行服务失去确认，需要重排和重新响应。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object changeCommand(Long id, String action, Command v) {
    lock();
    agentOrStaff();
    access.require(Set.of("approve", "reject").contains(action) ? "change.review" : "change.write");
    var x = change(id);
    var c = call(x.callId);
    String fp = fingerprint("CHANGE_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return detail("changes", id);
    version(x.version, v.version);
    String note = text(v.note, 1000);
    switch (action) {
      case "submit" -> {
        state(x.status, "DRAFT");
        state(c.status, "PLANNED");
        if (x.baseRevision != c.planRevision) throw new Problem(409, "STALE_PLAN");
        x.status = "SUBMITTED";
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "SUBMITTED").contains(x.status))
          throw new Problem(409, "INVALID_STATE");
        if (Set.of("CLOSED", "CANCELLED").contains(c.status))
          throw new Problem(409, "INVALID_STATE");
        x.status = "CANCELLED";
      }
      case "approve", "reject" -> {
        staff();
        state(x.status, "SUBMITTED");
        independent(x.createdBy);
        if (action.equals("approve")) {
          state(c.status, "PLANNED");
          if (x.baseRevision != c.planRevision) throw new Problem(409, "STALE_PLAN");
          c.eta = x.eta;
          c.etd = x.etd;
          c.planRevision++;
          c.version++;
          for (var s : db.query(ServiceOrder.class, "from ServiceOrder where callId=?1", c.id)) {
            if (REPLAN.contains(s.status)) {
              s.status = "CHANGE_PENDING";
              s.version++;
              event("SERVICE", s.id, "PLAN_CHANGED", note, s, c.departmentId);
            }
          }
          event("CALL", c.id, "PLAN_CHANGED", note, c, c.departmentId);
          x.status = "APPROVED";
        } else {
          if (Set.of("CLOSED", "CANCELLED").contains(c.status))
            throw new Problem(409, "INVALID_STATE");
          x.status = "REJECTED";
        }
        x.reviewedBy = access.current().id;
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    x.version++;
    event("CHANGE", id, action, note, x, c.departmentId);
    remember(v.requestKey, fp, id);
    return detail("changes", id);
  }

  /** 内部按挂靠当前版本编排服务；发送后的关键性、提供方和类型不可改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveService(Long id, ServiceInput v) {
    lock();
    staff();
    access.require("service.write");
    String fp = fingerprint("SERVICE_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return detail("services", prior);
    var c = call(v.callId);
    if (!Set.of("PLANNED", "ARRIVED").contains(c.status)) throw new Problem(409, "INVALID_STATE");
    var p = db.get(Agency.class, v.providerId);
    if (!p.kind.equals("PROVIDER") || !p.enabled || !Objects.equals(p.departmentId, c.departmentId))
      throw new Problem(400, "INVALID_PROVIDER");
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type=?1 and code=?2",
            "service",
            v.kind)
        .isEmpty()) throw new Problem(400, "INVALID_KIND");
    var s = id == null ? new ServiceOrder() : service(id);
    if (id != null) {
      version(s.version, v.version);
      state(s.status, "DRAFT");
      immutable(s.reference, v.reference);
      immutable(s.callId, c.id);
    }
    s.reference = text(v.reference, 80);
    s.callId = c.id;
    s.providerId = p.id;
    s.kind = v.kind;
    s.critical = Boolean.TRUE.equals(v.critical);
    s.windowStart = TimePolicy.time(v.windowStart);
    s.windowEnd = TimePolicy.time(v.windowEnd);
    TimePolicy.window(s.windowStart, s.windowEnd, c.eta, c.etd);
    s.planRevision = c.planRevision;
    s.status = "DRAFT";
    if (id == null) {
      s.createdBy = access.current().id;
      capacity(ServiceOrder.class);
      s.version = 1;
      db.save(s);
    } else s.version++;
    event("SERVICE", s.id, "SAVE", "", s, c.departmentId);
    remember(v.requestKey, fp, s.id);
    return detail("services", s.id);
  }

  /** 服务商独立响应自己的请求并填写事实；内部独立验收，异议允许补充证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object serviceCommand(Long id, String action, Command v) {
    lock();
    access.require(
        switch (action) {
          case "request", "replan", "cancel" -> "service.write";
          case "confirm", "decline" -> "service.respond";
          case "start", "report" -> "service.perform";
          case "accept", "dispute" -> "service.review";
          default -> throw new Problem(400, "INVALID_ACTION");
        });
    var s = service(id);
    var c = call(s.callId);
    String fp = fingerprint("SERVICE_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return detail("services", id);
    version(s.version, v.version);
    if (Set.of("CANCELLED", "CLOSED").contains(c.status)) throw new Problem(409, "INVALID_STATE");
    String note = text(v.note, 1000);
    switch (action) {
      case "request" -> {
        staff();
        state(s.status, "DRAFT");
        editableCall(c);
        if (!db.get(Agency.class, s.providerId).enabled)
          throw new Problem(409, "DISABLED_RESOURCE");
        TimePolicy.window(s.windowStart, s.windowEnd, c.eta, c.etd);
        currentPlan(s, c);
        s.status = "REQUESTED";
      }
      case "replan" -> {
        staff();
        state(s.status, "CHANGE_PENDING");
        editableCall(c);
        if (!db.get(Agency.class, s.providerId).enabled)
          throw new Problem(409, "DISABLED_RESOURCE");
        s.windowStart = TimePolicy.time(v.windowStart);
        s.windowEnd = TimePolicy.time(v.windowEnd);
        TimePolicy.window(s.windowStart, s.windowEnd, c.eta, c.etd);
        s.planRevision = c.planRevision;
        s.respondedBy = null;
        s.status = "REQUESTED";
      }
      case "cancel" -> {
        staff();
        if (!REPLAN.contains(s.status) && !s.status.equals("DECLINED"))
          throw new Problem(409, "INVALID_STATE");
        s.status = "CANCELLED";
      }
      case "confirm", "decline" -> {
        provider(s);
        state(s.status, "REQUESTED");
        editableCall(c);
        currentPlan(s, c);
        if (action.equals("confirm") && !db.get(Agency.class, s.providerId).enabled)
          throw new Problem(409, "DISABLED_RESOURCE");
        s.respondedBy = access.current().id;
        s.status = action.equals("confirm") ? "CONFIRMED" : "DECLINED";
      }
      case "start" -> {
        provider(s);
        state(s.status, "CONFIRMED");
        state(c.status, "ARRIVED");
        currentPlan(s, c);
        s.actualStart = TimePolicy.actual(v.at, c.arrivedAt, clock);
        s.status = "IN_PROGRESS";
      }
      case "report" -> {
        provider(s);
        if (!Set.of("IN_PROGRESS", "DISPUTED").contains(s.status))
          throw new Problem(409, "INVALID_STATE");
        if (!Set.of("ARRIVED", "DEPARTED").contains(c.status))
          throw new Problem(409, "INVALID_STATE");
        s.actualEnd = TimePolicy.actual(v.at, s.actualStart, clock);
        if (c.departedAt != null && s.actualEnd.isAfter(c.departedAt))
          throw new Problem(400, "AFTER_DEPARTURE");
        s.reportedBy = access.current().id;
        s.status = "REPORTED";
      }
      case "accept", "dispute" -> {
        staff();
        state(s.status, "REPORTED");
        independent(s.createdBy);
        independent(s.reportedBy);
        s.acceptedBy = access.current().id;
        s.status = action.equals("accept") ? "ACCEPTED" : "DISPUTED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    s.version++;
    event("SERVICE", id, action, note, s, c.departmentId);
    remember(v.requestKey, fp, id);
    return detail("services", id);
  }

  private void editableCall(PortCall c) {
    if (!Set.of("PLANNED", "ARRIVED").contains(c.status)) throw new Problem(409, "INVALID_STATE");
  }

  private void currentPlan(ServiceOrder s, PortCall c) {
    if (s.planRevision != c.planRevision) throw new Problem(409, "STALE_PLAN");
  }

  /** 统计只聚合可见挂靠与服务，不向服务方暴露他方任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var calls =
        access.role().permissions.contains("call.read")
            ? db.all(PortCall.class).stream().filter(this::visible).toList()
            : List.<PortCall>of();
    var services =
        access.role().permissions.contains("service.read")
            ? db.all(ServiceOrder.class).stream().filter(this::serviceVisible).toList()
            : List.<ServiceOrder>of();
    Map<String, Long> cs = new TreeMap<>(), ss = new TreeMap<>();
    calls.forEach(c -> cs.merge(c.status, 1L, Long::sum));
    services.forEach(s -> ss.merge(s.status, 1L, Long::sum));
    return Map.of(
        "calls",
        calls.size(),
        "services",
        services.size(),
        "callStates",
        cs,
        "serviceStates",
        ss,
        "replan",
        services.stream().filter(s -> s.status.equals("CHANGE_PENDING")).count(),
        "pendingReview",
        services.stream().filter(s -> s.status.equals("REPORTED")).count(),
        "disputed",
        services.stream().filter(s -> s.status.equals("DISPUTED")).count(),
        "criticalOpen",
        services.stream()
            .filter(s -> s.critical && !Set.of("ACCEPTED", "CANCELLED").contains(s.status))
            .count());
  }

  private String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }

  private void capacity(Class<?> c) {
    if (db.all(c).size() >= Integer.parseInt(setting("maxRecords")))
      throw new Problem(409, "CAPACITY_LIMIT");
  }

  private String text(String s, int max) {
    return AdminService.text(s, max);
  }

  private String choice(String s, String... values) {
    if (s == null || !Arrays.asList(values).contains(s)) throw new Problem(400, "INVALID_KIND");
    return s;
  }

  private void state(String s, String required) {
    if (!required.equals(s)) throw new Problem(409, "INVALID_STATE");
  }

  private void independent(Long id) {
    if (Objects.equals(id, access.current().id))
      throw new Problem(409, "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void immutable(Object a, Object b) {
    if (!Objects.equals(a, b)) throw new Problem(409, "IMMUTABLE_IDENTITY");
  }

  private void version(long a, Long b) {
    if (b == null || a != b) throw new Problem(409, "STALE_VERSION");
  }

  private boolean contains(String search, String... values) {
    if (search.length() > 200) throw new Problem(400, "INVALID_INPUT");
    return Arrays.stream(values)
        .anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)));
  }

  private Object page(List<?> rows, int page, int size, String sort) {
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || !Set.of("newest", "reference").contains(sort)) throw new Problem(400, "INVALID_PAGE");
    var sorted =
        rows.stream()
            .sorted(
                (a, b) ->
                    sort.equals("reference")
                        ? reference(a).compareTo(reference(b))
                        : Long.compare(id(b), id(a)))
            .toList();
    int start = Math.min(sorted.size(), page * size);
    return Map.of(
        "items",
        sorted.subList(start, Math.min(sorted.size(), start + size)),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  private String reference(Object o) {
    return switch (o) {
      case PortCall c -> c.reference;
      case Agency a -> a.reference;
      case Vessel v -> v.reference;
      case ServiceOrder s -> s.reference;
      case PlanChange c -> c.reference;
      default -> throw new IllegalStateException();
    };
  }

  private Long id(Object o) {
    return switch (o) {
      case PortCall c -> c.id;
      case Agency a -> a.id;
      case Vessel v -> v.id;
      case ServiceOrder s -> s.id;
      case PlanChange c -> c.id;
      default -> throw new IllegalStateException();
    };
  }

  private String fingerprint(String action, Long id, Object body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (access.current().id
                              + "|"
                              + action
                              + "|"
                              + id
                              + "|"
                              + mapper.writeValueAsString(body))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Long retry(String key, String fp) {
    if (key == null
        || !key.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var rows =
        db.query(
            CommandRecord.class,
            "from CommandRecord where requestKey=?1",
            key.toLowerCase(Locale.ROOT));
    if (rows.isEmpty()) return null;
    if (!rows.getFirst().fingerprint.equals(fp)) throw new Problem(409, "REQUEST_KEY_REUSED");
    return rows.getFirst().resultId;
  }

  private void remember(String key, String fp, Long result) {
    var c = new CommandRecord();
    c.requestKey = key.toLowerCase(Locale.ROOT);
    c.fingerprint = fp;
    c.resultId = result;
    db.save(c);
  }

  private void event(
      String type, Long id, String action, String note, Object snapshot, Long department) {
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.action = action;
    e.actorId = access.current().id;
    e.note = note;
    e.snapshot = mapper.writeValueAsString(snapshot);
    e.createdAt = BusinessTime.now(clock);
    db.save(e);
    access.audit(type + "_" + action, id, department);
  }
}
