# Trino input plugin for Embulk

Trino input plugin for Embulk loads records from Trino using trino-client.

## Overview

* **Plugin type**: input
* **Resume supported**: no

## How to install

```
java -jar /path/to/embulk.jar install com.reproio.embulk:embulk-input-trino:<version>
```

## Configuration

- **host**: database host name (string, required)
- **port**: database port number (integer, default: 8080)
- **user**: database login user name (string, required)
- **catalog**: database catalog (string, default: "")
- **schema**: database schema (string, default: "")
- **query**: SQL to run (string)
- **column_options**: advanced: key-value pairs where key is a column name and value is options for the column.
  - **value_type**: embulk get values from database as this value_type. Typically, the value_type determines `getXXX` method of `java.sql.PreparedStatement`.
  (string, default: depends on the sql type of the column. Available values options are: `long`, `double`, `float`, `decimal`, `boolean`, `string`, `json`, `date`, `time`, `timestamp`)
  - **type**: Column values are converted to this embulk type.
  Available values options are: `boolean`, `long`, `double`, `string`, `json`, `timestamp`).
  By default, the embulk type is determined according to the sql type of the column (or value_type if specified).
  - **timestamp_format**: If the sql type of the column is `date`/`time`/`datetime` and the embulk type is `string`, column values are formatted by this timestamp_format. And if the embulk type is `timestamp`, this timestamp_format may be used in the output plugin. For example, stdout plugin use the timestamp_format, but *csv formatter plugin doesn't use*. (string, default : `%Y-%m-%d` for `date`, `%H:%M:%S` for `time`, `%Y-%m-%d %H:%M:%S` for `timestamp`)
  - **timezone**: If the sql type of the column is `date`/`time`/`datetime` and the embulk type is `string`, column values are formatted in this timezone.
(string, value of default_timezone option is used by default)


### Example


```yaml
in:
  type: trino
  host: trino-cordinator
  catalog: store
  schema: public
  query: |
    SELECT
      trim(upper(url_decode(keyword))) AS keyword,
      count(*) as count
    FROM search
    CROSS JOIN UNNEST(split(keywords, ',')) AS t (keyword)
    WHERE log_date >= (CURRENT_DATE - INTERVAL '90' DAY)
     AND length(keywords) != 256
    group by keyword
    having count(*) >= 10
    order by count(*) desc
out:
  type: stdout
```


## Build

```console
$ ./gradlew build
```

## Release

TODO

```console
$ ./gradlew publish
```

## Local Development

Copy Maven dependencies to `$HOME/.m2/repository`.

```
$ ./gradlew cacheToMavenLocal
$ ./gradlew publishToMavenLocal
```

Create `$HOME/.embulk/embulk.properties`.

```
m2_repo=<path to .m2/repository>
jruby=file://<path to jruby.jar>
plugins.input.trino=maven:com.reproio.embulk:trino:<version>
```
