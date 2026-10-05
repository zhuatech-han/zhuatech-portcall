// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import jakarta.persistence.*;

/** 组织录入的船舶名称与内部编号，不证明船籍或所有权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "vessel")
public class Vessel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = true, length = 80)
  public String reference;

  @Column(name = "name", nullable = true, length = 120)
  public String name;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "created_by", nullable = true)
  public Long createdBy;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "version", nullable = true)
  public long version;
}
