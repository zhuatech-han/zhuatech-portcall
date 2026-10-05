// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import jakarta.persistence.*;
import java.time.Instant;

/** 按计划版本和时间窗口请求服务；只有绑定服务商能响应与作业。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "service_order")
public class ServiceOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = true, length = 80)
  public String reference;

  @Column(name = "call_id", nullable = true)
  public Long callId;

  @Column(name = "provider_id", nullable = true)
  public Long providerId;

  @Column(name = "kind", nullable = true, length = 60)
  public String kind;

  @Column(name = "critical", nullable = true)
  public boolean critical;

  @Column(name = "window_start", nullable = true)
  public Instant windowStart;

  @Column(name = "window_end", nullable = true)
  public Instant windowEnd;

  @Column(name = "actual_start", nullable = false)
  public Instant actualStart;

  @Column(name = "actual_end", nullable = false)
  public Instant actualEnd;

  @Column(name = "status", nullable = true, length = 30)
  public String status;

  @Column(name = "plan_revision", nullable = true)
  public long planRevision;

  @Column(name = "created_by", nullable = true)
  public Long createdBy;

  @Column(name = "responded_by", nullable = false)
  public Long respondedBy;

  @Column(name = "reported_by", nullable = false)
  public Long reportedBy;

  @Column(name = "accepted_by", nullable = false)
  public Long acceptedBy;

  @Column(name = "version", nullable = true)
  public long version;
}
