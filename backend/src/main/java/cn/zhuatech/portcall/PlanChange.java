// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import jakarta.persistence.*;
import java.time.Instant;

/** 对特定挂靠版本提出时间变更；批准后服务确认失效并留痕。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "plan_change")
public class PlanChange {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = true, length = 80)
  public String reference;

  @Column(name = "call_id", nullable = true)
  public Long callId;

  @Column(name = "eta", nullable = true)
  public Instant eta;

  @Column(name = "etd", nullable = true)
  public Instant etd;

  @Column(name = "base_revision", nullable = true)
  public long baseRevision;

  @Column(name = "status", nullable = true, length = 30)
  public String status;

  @Column(name = "created_by", nullable = true)
  public Long createdBy;

  @Column(name = "reviewed_by", nullable = false)
  public Long reviewedBy;

  @Column(name = "version", nullable = true)
  public long version;
}
