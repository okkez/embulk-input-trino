package io.github.okkez.embulk.input.trino;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import org.embulk.spi.Column;
import org.embulk.spi.ColumnVisitor;
import org.embulk.spi.PageBuilder;

public class TrinoColumnVisitor implements ColumnVisitor {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd[ HH:mm:ss[.n]]");
  private final List<OutputColumn> outputColumns;
  private final PageBuilder pageBuilder;
  private final List<Object> row;

  public TrinoColumnVisitor(
      List<OutputColumn> outputColumns, PageBuilder pageBuilder, List<Object> row) {
    this.outputColumns = outputColumns;
    this.pageBuilder = pageBuilder;
    this.row = row;
  }

  @Override
  public void booleanColumn(Column column) {
    pageBuilder.setBoolean(column, Boolean.parseBoolean((String) row.get(column.getIndex())));
  }

  @Override
  public void longColumn(Column column) {
    pageBuilder.setLong(column, Long.parseLong((String) row.get(column.getIndex())));
  }

  @Override
  public void doubleColumn(Column column) {
    pageBuilder.setDouble(column, Double.parseDouble((String) row.get(column.getIndex())));
  }

  @Override
  public void stringColumn(Column column) {
    pageBuilder.setString(column, (String) row.get(column.getIndex()));
  }

  @Override
  public void timestampColumn(Column column) {
    TrinoType trinoType = outputColumns.get(column.getIndex()).type();
    String value =
        switch (trinoType) {
          case TIME, TIME_WITH_TIME_ZONE ->
              String.format("%s %s", today(), row.get(column.getIndex()));
          default -> (String) row.get(column.getIndex());
        };
    TemporalAccessor ta =
        DATE_TIME_FORMAT.parseBest(
            value, LocalDate::from, LocalDateTime::from, ZonedDateTime::from);
    Instant instant = null;
    if (ta instanceof LocalDate v) {
      instant = v.atStartOfDay().toInstant(ZoneOffset.UTC);
    } else if (ta instanceof LocalDateTime v) {
      instant = v.toInstant(ZoneOffset.UTC);
    } else if (ta instanceof ZonedDateTime v) {
      instant = v.toInstant();
    }

    if (instant == null) {
      pageBuilder.setNull(column);
    } else {
      pageBuilder.setTimestamp(column, instant);
    }
  }

  @Override
  public void jsonColumn(Column column) {
    throw new UnsupportedOperationException("JSON type is not supported yet.");
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.systemDefault());
  }
}
