package it.fvaleri.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;

public class EchoSinkConnector extends SinkConnector {
    private static final String VERSION = EchoSinkConnector.class.getPackage().getImplementationVersion();

    public static final String LEVEL_CONFIG = "level";
    public static final String FAIL_TASK_AFTER_RECORDS_CONFIG = "fail.task.after.records";

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
        .define(LEVEL_CONFIG, Type.STRING, "INFO", Importance.HIGH, "Log level on which the received records will be printed. If not specified, INFO will be used.")
        .define(FAIL_TASK_AFTER_RECORDS_CONFIG, Type.LONG, 0L, Importance.HIGH, "The connector task will fail after receiving specified number of records. If not specified or set to 0, it will not fail intentionally.");

    private String logLevel;
    private long failTaskAfterRecords;

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public void start(Map<String, String> props) {
        AbstractConfig parsedConfig = new AbstractConfig(CONFIG_DEF, props);
        logLevel = parsedConfig.getString(LEVEL_CONFIG);
        failTaskAfterRecords = parsedConfig.getLong(FAIL_TASK_AFTER_RECORDS_CONFIG);
    }

    @Override
    public Class<? extends Task> taskClass() {
        return EchoSinkTask.class;
    }

    // the job is partitioned among maxTasks tasks
    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        ArrayList<Map<String, String>> configs = new ArrayList<>();
        Map<String, String> config = new HashMap<>(1);

        if (logLevel != null)
            config.put(LEVEL_CONFIG, logLevel);

        if (failTaskAfterRecords > 0)
            config.put(FAIL_TASK_AFTER_RECORDS_CONFIG, String.valueOf(failTaskAfterRecords));

        for (int i = 0; i < maxTasks; i++)
            configs.add(config);
        return configs;
    }

    @Override
    public void stop() {
        // noop
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }
}
