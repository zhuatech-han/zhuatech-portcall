// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.portcall;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 预计窗口与实际事实的边界检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class TimePolicyTest {
  final Instant a = Instant.parse("2026-10-05T00:00:00Z"), b = a.plusSeconds(3600);
  final Clock clock = Clock.fixed(b, ZoneOffset.UTC);

  @Test
  void increasingWindowAccepted() {
    assertDoesNotThrow(() -> TimePolicy.window(a, b, a, b));
  }

  @Test
  void zeroOrReversedWindowRejected() {
    assertThrows(Problem.class, () -> TimePolicy.interval(a, a));
    assertThrows(Problem.class, () -> TimePolicy.interval(b, a));
  }

  @Test
  void beforePlanRejected() {
    assertThrows(Problem.class, () -> TimePolicy.window(a.minusSeconds(1), b, a, b));
  }

  @Test
  void afterPlanRejected() {
    assertThrows(Problem.class, () -> TimePolicy.window(a, b.plusSeconds(1), a, b));
  }

  @Test
  void futureActualRejected() {
    assertThrows(Problem.class, () -> TimePolicy.actual(b.plusSeconds(1), a, clock));
  }

  @Test
  void priorMilestoneRejected() {
    assertThrows(Problem.class, () -> TimePolicy.actual(a.minusSeconds(1), a, clock));
  }

  @Test
  void databaseMicrosecondsNormalized() {
    assertEquals(a.plusNanos(123456000), TimePolicy.time(a.plusNanos(123456789)));
  }

  @Test
  void emptyAndExcessivePlansRejected() {
    assertThrows(Problem.class, () -> TimePolicy.time(null));
    assertThrows(Problem.class, () -> TimePolicy.interval(a, a.plus(Duration.ofDays(91))));
  }
}
