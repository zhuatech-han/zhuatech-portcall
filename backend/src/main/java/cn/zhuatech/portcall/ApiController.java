// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 有限业务路由；写入由领域服务执行权限与事务检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final CallService calls;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(CallService calls, AdminService admin, AccessService access, Store db) {
    this.calls = calls;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 当前范围表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return calls.options();
  }

  /** 业务列表搜索、筛选及分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:calls|changes|services|vessels|agencies}")
  public Object list(
      @PathVariable String type,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return calls.list(type, search, status, kind, page, size, sort);
  }

  /** 详情与真实不可变事件记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:calls|changes|services}/{id}")
  public Object detail(@PathVariable String type, @PathVariable Long id) {
    return calls.detail(type, id);
  }

  /** 船舶与协作机构建档。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:vessels|agencies}")
  public Object master(@PathVariable String type, @RequestBody CallService.MasterInput v) {
    return calls.saveMaster(type, null, v);
  }

  /** 目录改名、启停与版本检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/{type:vessels|agencies}/{id}")
  public Object master(
      @PathVariable String type, @PathVariable Long id, @RequestBody CallService.MasterInput v) {
    return calls.saveMaster(type, id, v);
  }

  /** 删除未引用目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/{type:vessels|agencies}/{id}")
  public Object remove(@PathVariable String type, @PathVariable Long id) {
    calls.deleteMaster(type, id);
    return Map.of("ok", true);
  }

  /** 创建挂靠草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/calls")
  public Object call(@RequestBody CallService.CallInput v) {
    return calls.saveCall(null, v);
  }

  /** 更新未提交挂靠草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/calls/{id}")
  public Object call(@PathVariable Long id, @RequestBody CallService.CallInput v) {
    return calls.saveCall(id, v);
  }

  /** 提交、审核、实际到离、独立关单和取消。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/calls/{id}/commands/{action}")
  public Object call(
      @PathVariable Long id, @PathVariable String action, @RequestBody CallService.Command v) {
    return calls.callCommand(id, action, v);
  }

  /** 创建预计时间变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/changes")
  public Object change(@RequestBody CallService.ChangeInput v) {
    return calls.saveChange(null, v);
  }

  /** 更新未提交变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/changes/{id}")
  public Object change(@PathVariable Long id, @RequestBody CallService.ChangeInput v) {
    return calls.saveChange(id, v);
  }

  /** 变更提交、独立审核和取消。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/changes/{id}/commands/{action}")
  public Object change(
      @PathVariable Long id, @PathVariable String action, @RequestBody CallService.Command v) {
    return calls.changeCommand(id, action, v);
  }

  /** 新建服务窗口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/services")
  public Object service(@RequestBody CallService.ServiceInput v) {
    return calls.saveService(null, v);
  }

  /** 更新未发送服务草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/services/{id}")
  public Object service(@PathVariable Long id, @RequestBody CallService.ServiceInput v) {
    return calls.saveService(id, v);
  }

  /** 发送、确认、重排、作业回报和独立验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/services/{id}/commands/{action}")
  public Object service(
      @PathVariable Long id, @PathVariable String action, @RequestBody CallService.Command v) {
    return calls.serviceCommand(id, action, v);
  }

  /** 导出沿用详情范围，不插入推广载荷。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:calls|changes|services}/{id}/report.json")
  @Transactional(readOnly = true)
  public ResponseEntity<Object> export(@PathVariable String type, @PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "-" + id + ".json")
        .body(calls.detail(type, id));
  }

  /** 可见工作与待处理统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return calls.dashboard();
  }

  /** 内部审计；SELF只显示本人，机构账号一律禁止。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    calls.staff();
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .limit(500)
        .toList();
  }

  /** 系统管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object admin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object admin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源校验修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object admin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 保留基础目录、最后管理员和历史引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object delete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
