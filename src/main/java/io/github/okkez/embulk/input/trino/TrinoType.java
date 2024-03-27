package io.github.okkez.embulk.input.trino;

import org.embulk.spi.type.Type;
import org.embulk.spi.type.Types;

@SuppressWarnings("nocomment")
// https://trino.io/docs/current/language/types.html
public enum TrinoType {
  BOOLEAN,
  TINYINT,
  SMALLINT,
  INTEGER,
  INT,
  BIGINT,
  REAL,
  DOUBLE,
  DECIMAL,
  VARCHAR,
  CHAR,
  VARBINARY,
  DATE,
  TIMESTAMP_WITH_TIME_ZONE,
  TIMESTAMP,
  TIME_WITH_TIME_ZONE,
  TIME,
  INTERVAL_YEAR_TO_MONTH,
  INTERVAL_DAY_TO_SECOND,
  JSON,
  ARRAY,
  MAP,
  ROW;

  public Type toEmbulkType() {
    return switch (this) {
      case BOOLEAN -> Types.BOOLEAN;
      case TINYINT, SMALLINT, INTEGER, INT, BIGINT, DECIMAL -> Types.LONG;
      case REAL, DOUBLE -> Types.DOUBLE;
      case VARCHAR, CHAR, VARBINARY -> Types.STRING;
      case DATE,
              TIME,
              TIME_WITH_TIME_ZONE,
              TIMESTAMP,
              TIMESTAMP_WITH_TIME_ZONE,
              INTERVAL_YEAR_TO_MONTH,
              INTERVAL_DAY_TO_SECOND ->
          Types.TIMESTAMP;
      case JSON, ARRAY, MAP, ROW -> Types.JSON;
    };
  }

  public static TrinoType of(String typeName) {
    try {
      return valueOf(typeName.toUpperCase());
    } catch (IllegalArgumentException ex) {
      for (TrinoType type : TrinoType.values()) {
        if (typeName.startsWith(type.name())) {
          return type;
        }
      }
    }
    throw new IllegalArgumentException(typeName);
  }
}
