package io.github.okkez.embulk.input.trino;

import static io.trino.client.StatementClientFactory.newStatementClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import io.trino.client.ClientSession;
import io.trino.client.StatementClient;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import okhttp3.OkHttpClient;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.spi.BufferAllocator;
import org.embulk.spi.Exec;
import org.embulk.spi.InputPlugin;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;
import org.embulk.util.config.Config;
import org.embulk.util.config.ConfigDefault;
import org.embulk.util.config.ConfigMapper;
import org.embulk.util.config.ConfigMapperFactory;
import org.embulk.util.config.Task;
import org.embulk.util.config.TaskMapper;

public class TrinoInputPlugin implements InputPlugin {

  private static final ConfigMapperFactory CONFIG_MAPPER_FACTORY =
      ConfigMapperFactory.builder().addDefaultModules().build();
  private static final ConfigMapper CONFIG_MAPPER = CONFIG_MAPPER_FACTORY.createConfigMapper();
  private static final TaskMapper TASK_MAPPER = CONFIG_MAPPER_FACTORY.createTaskMapper();

  private final OkHttpClient httpClient =
      new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(5))
          .readTimeout(Duration.ofSeconds(5))
          .writeTimeout(Duration.ofSeconds(5))
          .build();

  private final List<OutputColumn> outputColumns = new ArrayList<>();

  public interface PluginTask extends Task {
    @Config("host")
    @ConfigDefault("\"localhost\"")
    String getHost();

    @Config("port")
    @ConfigDefault("8080")
    int getPort();

    @Config("catalog")
    @ConfigDefault("\"system\"")
    String getCatalog();

    @Config("schema")
    @ConfigDefault("\"default\"")
    String getSchema();

    @Config("query")
    String getQuery();

    @Config("user")
    @ConfigDefault("\"embulk\"")
    String getUser();

    @Config("column_options")
    @ConfigDefault("{}")
    Map<String, ConfigSource> getColumnOptions();
  }

  @Override
  public ConfigDiff transaction(ConfigSource config, Control control) {
    final PluginTask task = CONFIG_MAPPER.map(config, PluginTask.class);
    var schemaBuilder = Schema.builder();
    if (task.getColumnOptions().isEmpty()) {
      buildOutputColumns(
          task,
          (columnName, columnType) -> {
            outputColumns.add(new OutputColumn(columnName, TrinoType.of(columnType), columnType));
            schemaBuilder.add(columnName, TrinoType.of(columnType).toEmbulkType());
          });
    } else {
      task.getColumnOptions()
          .forEach(
              (columnName, configSource) -> {
                String columnType = configSource.get(String.class, "type");
                outputColumns.add(
                    new OutputColumn(columnName, TrinoType.of(columnType), columnType));
                schemaBuilder.add(columnName, TrinoType.of(columnType).toEmbulkType());
              });
    }
    var s = schemaBuilder.build();
    System.out.println(s.getColumns());
    return resume(task.toTaskSource(), s, 1, control);
  }

  @Override
  public ConfigDiff resume(TaskSource taskSource, Schema schema, int taskCount, Control control) {
    final PluginTask task = TASK_MAPPER.map(taskSource, PluginTask.class);
    control.run(task.toTaskSource(), schema, taskCount);
    return CONFIG_MAPPER_FACTORY.newConfigDiff();
  }

  @Override
  public void cleanup(
      TaskSource taskSource, Schema schema, int taskCount, List<TaskReport> successTaskReports) {
    // Do nothing
  }

  @Override
  public TaskReport run(TaskSource taskSource, Schema schema, int taskIndex, PageOutput output) {
    final PluginTask task = TASK_MAPPER.map(taskSource, PluginTask.class);
    final ClientSession session = getClientSession(task);
    final OkHttpClient.Builder b = httpClient.newBuilder();
    final BufferAllocator allocator = Exec.getBufferAllocator();

    try (final PageBuilder pageBuilder = Exec.getPageBuilder(allocator, schema, output);
        final StatementClient statement =
            newStatementClient(b.build(), session, task.getQuery(), Optional.empty())) {
      while (statement.isRunning()) {
        final Iterable<List<Object>> data = statement.currentData().getData();
        if (data != null) {
          data.forEach(
              row -> {
                schema.visitColumns(new TrinoColumnVisitor(outputColumns, pageBuilder, row));
                pageBuilder.addRecord();
              });
        }
        statement.advance();
      }
      pageBuilder.finish();
    }
    return CONFIG_MAPPER_FACTORY.newTaskReport();
  }

  @Override
  public ConfigDiff guess(ConfigSource config) {
    return CONFIG_MAPPER_FACTORY.newConfigDiff();
  }

  private ClientSession getClientSession(PluginTask task) {
    try {
      URI uri = new URI("http", null, task.getHost(), task.getPort(), null, null, null);
      return ClientSession.builder()
          .server(uri)
          .catalog(task.getCatalog())
          .schema(task.getSchema())
          .user(Optional.of(task.getUser()))
          .locale(Locale.getDefault())
          .timeZone(ZoneId.systemDefault())
          .build();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private void buildOutputColumns(PluginTask task, BiConsumer<String, String> biConsumer) {
    ClientSession session = getClientSession(task);
    OkHttpClient.Builder b = httpClient.newBuilder();
    String query = "explain (FORMAT JSON) " + task.getQuery();
    try (StatementClient statement =
        newStatementClient(b.build(), session, query, Optional.empty())) {
      while (statement.isRunning()) {
        Iterable<List<Object>> data = statement.currentData().getData();
        if (data != null) {
          List<Object> row = data.iterator().next(); // The result of EXPLAIN has only 1 row
          String queryPlanJson = (String) row.get(0);
          ObjectMapper mapper = new ObjectMapper();
          JsonNode node = mapper.readTree(queryPlanJson);
          List<JsonNode> outputs = new ArrayList<>();
          List<JsonNode> details = new ArrayList<>();
          node.get("0").get("outputs").forEach(outputs::add);
          node.get("0").get("details").forEach(details::add);
          if (details.isEmpty()) {
            outputs.forEach(
                output -> {
                  biConsumer.accept(
                      output.get("symbol").textValue(), output.get("type").textValue());
                });
          } else {
            Streams.forEachPair(
                outputs.stream(),
                details.stream(),
                (output, detail) -> {
                  var values = detail.textValue().split(" := ", 2);
                  assert values[1].equals(output.get("symbol").textValue());
                  String columnName = values[0];
                  biConsumer.accept(columnName, output.get("type").textValue());
                });
          }
        }
        statement.advance();
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
