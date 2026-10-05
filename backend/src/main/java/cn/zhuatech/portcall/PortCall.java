// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import jakarta.persistence.*;
import java.time.Instant;

/** 独立审核后的挂靠计划及实际到离事实，时刻统一UTC微秒。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "port_call")
public class PortCall {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = true, length = 80)
  public String reference;

  @Column(name = "vessel_id", nullable = true)
  public Long vesselId;

  @Column(name = "agent_id", nullable = true)
  public Long agentId;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "location", nullable = true, length = 120)
  public String location;

  @Column(name = "eta", nullable = true)
  public Instant eta;

  @Column(name = "etd", nullable = true)
  public Instant etd;

  @Column(name = "arrived_at", nullable = false)
  public Instant arrivedAt;

  @Column(name = "departed_at", nullable = false)
  public Instant departedAt;

  @Column(name = "status", nullable = true, length = 30)
  public String status;

  @Column(name = "created_by", nullable = true)
  public Long createdBy;

  @Column(name = "approved_by", nullable = false)
  public Long approvedBy;

  @Column(name = "closed_by", nullable = false)
  public Long closedBy;

  @Column(name = "plan_revision", nullable = true)
  public long planRevision;

  @Column(name = "version", nullable = true)
  public long version;
}
