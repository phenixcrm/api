package com.ameriglide.phenix.model;

import net.inetalliance.sql.DateTimeInterval;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

public enum Range {
  DAY() {
    @Override
    public DateTimeInterval toDateTimeInterval() {
      var now = LocalDate.now().atStartOfDay();
      return new DateTimeInterval(now, now.plusDays(1));
    }
  },
  WEEK() {
    @Override
    public DateTimeInterval toDateTimeInterval() {

      final LocalDateTime start = LocalDate.now().with(DayOfWeek.SUNDAY).atStartOfDay();
      return new DateTimeInterval(start, start.plusWeeks(1));
    }
  },
  MONTH() {
    @Override
    public DateTimeInterval toDateTimeInterval() {
      final LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
      return new DateTimeInterval(start, start.plusMonths(1));
    }
  },
  YEAR() {
    @Override
    public DateTimeInterval toDateTimeInterval() {
      final LocalDateTime start = LocalDate.now().withDayOfYear(1).atStartOfDay();
      return new DateTimeInterval(start, start.plusYears(1));
    }
  },
  N30() {
    public DateTimeInterval toDateTimeInterval() {
      final LocalDateTime start = LocalDate.now().atStartOfDay();
      return new DateTimeInterval(start, start.plusDays(30));
    }
  },
  N90() {
    public DateTimeInterval toDateTimeInterval() {
      final LocalDateTime start = LocalDate.now().atStartOfDay();
      return new DateTimeInterval(start, start.plusDays(90));
    }
  },
  L30() {
    @Override
    public DateTimeInterval toDateTimeInterval() {
      final LocalDateTime start = LocalDate.now().atStartOfDay();
      return new DateTimeInterval(start.minusDays(30), start);
    }
  },
  L90() {
    @Override
    public DateTimeInterval toDateTimeInterval() {
      final LocalDateTime start = LocalDate.now().atStartOfDay();
      return new DateTimeInterval(start.minusDays(90), start);
    }
  };

  abstract public DateTimeInterval toDateTimeInterval();
}
