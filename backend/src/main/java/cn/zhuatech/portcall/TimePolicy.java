// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import java.time.*;
import java.time.temporal.ChronoUnit;

/** 区分预计窗口与已发生时刻，统一数据库微秒精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class TimePolicy {
  private TimePolicy() {}

  /** 标准化UTC输入，不接受空值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant time(Instant v) {
    if (v == null) throw new Problem(400, "INVALID_TIME");
    return v.truncatedTo(ChronoUnit.MICROS);
  }

  /** 计划与服务窗口须递增，最多90日。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void interval(Instant a, Instant b) {
    if (a == null
        || b == null
        || !b.isAfter(a)
        || Duration.between(a, b).compareTo(Duration.ofDays(90)) > 0)
      throw new Problem(400, "INVALID_INTERVAL");
  }

  /** 请求窗口是内部协调约定，须落在当前挂靠预计区间内。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void window(Instant start, Instant end, Instant eta, Instant etd) {
    interval(start, end);
    if (start.isBefore(eta) || end.isAfter(etd)) throw new Problem(400, "OUTSIDE_PLAN");
  }

  /** 实际时刻必须已发生且不早于前序事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant actual(Instant value, Instant floor, Clock clock) {
    var t = time(value);
    if (t.isAfter(BusinessTime.now(clock)) || (floor != null && t.isBefore(floor)))
      throw new Problem(400, "INVALID_ACTUAL_TIME");
    return t;
  }
}
