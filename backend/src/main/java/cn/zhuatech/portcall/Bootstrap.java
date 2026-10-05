// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化岗位、服务类型和系统目录，无船舶或港口业务演示数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${portcall.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 注册权限并事务建立管理员；已有库不重置身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    String[][] permissions = {
      {"catalog.write", "维护船舶和机构"},
      {"call.read", "查看挂靠"},
      {"call.write", "提交挂靠计划"},
      {"call.approve", "独立审核计划"},
      {"call.event", "记录实际到离港"},
      {"call.close", "独立关单"},
      {"change.read", "查看计划变更"},
      {"change.write", "提出计划变更"},
      {"change.review", "独立审核变更"},
      {"service.read", "查看服务请求"},
      {"service.write", "编排服务请求"},
      {"service.respond", "服务商确认或拒绝"},
      {"service.perform", "服务商作业与回报"},
      {"service.review", "独立验收服务"},
      {"dashboard", "协同统计"},
      {"export", "范围内证据导出"},
      {"audit", "内部审计"},
      {"admin", "系统管理"}
    };
    var all = new HashSet<String>();
    for (var row : permissions) {
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
      all.add(p.code);
    }
    var administrator = role("管理员", "ALL", all);
    role(
        "挂靠协调",
        "DEPARTMENT",
        Set.of(
            "catalog.write",
            "call.read",
            "call.write",
            "call.event",
            "change.read",
            "change.write",
            "service.read",
            "service.write",
            "dashboard",
            "export",
            "audit"));
    role(
        "独立复核",
        "DEPARTMENT",
        Set.of(
            "call.read",
            "call.approve",
            "call.close",
            "change.read",
            "change.review",
            "service.read",
            "service.review",
            "dashboard",
            "export",
            "audit"));
    role(
        "船舶代理",
        "SELF",
        Set.of(
            "call.read",
            "call.write",
            "change.read",
            "change.write",
            "service.read",
            "dashboard",
            "export"));
    role(
        "服务提供方",
        "SELF",
        Set.of(
            "call.read",
            "service.read",
            "service.respond",
            "service.perform",
            "dashboard",
            "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.departmentId = d.id;
    a.roleId = administrator.id;
    a.enabled = true;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    String[][] menus = {
      {"calls", "船舶挂靠", "Port calls", "call.read"},
      {"changes", "计划变更", "Plan changes", "change.read"},
      {"services", "服务请求", "Services", "service.read"},
      {"vessels", "船舶目录", "Vessels", "catalog.write"},
      {"agencies", "协作机构", "Agencies", "catalog.write"},
      {"dashboard", "协同统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "服务类型", "Service types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "PortCall 挂靠协同", "maxRecords", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] types = {
      {"ARRIVAL", "到港相关服务", "Arrival service"},
      {"CARGO", "货物作业协调", "Cargo service"},
      {"SUPPLIES", "补给服务", "Provisions"},
      {"DEPARTURE", "离港相关服务", "Departure service"}
    };
    for (var row : types) {
      var e = new DictionaryEntry();
      e.type = "service";
      e.code = row[0];
      e.name = row[1];
      e.nameEn = row[2];
      db.save(e);
    }
  }

  private AccessRole role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}
