/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.tempto.fulfillment.table.kafka;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.prestosql.tempto.configuration.Configuration;
import io.prestosql.tempto.fulfillment.table.MutableTableRequirement;
import io.prestosql.tempto.fulfillment.table.TableDefinition;
import io.prestosql.tempto.fulfillment.table.TableHandle;
import io.prestosql.tempto.fulfillment.table.TableInstance;
import io.prestosql.tempto.fulfillment.table.TableManager;
import io.prestosql.tempto.internal.fulfillment.table.TableName;
import io.prestosql.tempto.query.QueryExecutor;
import io.prestosql.tempto.query.QueryResult;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@TableManager.Descriptor(tableDefinitionClass = KafkaTableDefinition.class, type = "KAFKA")
@Singleton
public class KafkaTableManager
        implements TableManager<KafkaTableDefinition>
{
    private final String databaseName;
    private final QueryExecutor prestoQueryExecutor;
    private final Configuration brokerConfiguration;
    private final Configuration zookeeperConfiguration;
    private final String prestoKafkaCatalog;

    @Inject
    public KafkaTableManager(
            @Named("databaseName") String databaseName,
            @Named("broker") Configuration brokerConfiguration,
            @Named("zookeeper") Configuration zookeeperConfiguration,
            @Named("presto_database_name") String prestoDatabaseName,
            @Named("presto_kafka_catalog") String prestoKafkaCatalog,
            Injector injector)
    {
        this.databaseName = requireNonNull(databaseName, "databaseName is null");
        this.brokerConfiguration = requireNonNull(brokerConfiguration, "brokerConfiguration is null");
        this.zookeeperConfiguration = requireNonNull(zookeeperConfiguration, "zookeeperConfiguration is null");
        requireNonNull(injector, "injector is null");
        requireNonNull(prestoDatabaseName, "prestoDatabaseName is null");
        this.prestoQueryExecutor = injector.getInstance(Key.get(QueryExecutor.class, Names.named(prestoDatabaseName)));
        this.prestoKafkaCatalog = requireNonNull(prestoKafkaCatalog, "prestoKafkaCatalog is null");
    }

    @Override
    public TableInstance<KafkaTableDefinition> createImmutable(KafkaTableDefinition tableDefinition, TableHandle tableHandle)
    {
        verifyTableExistsInPresto(tableHandle.getSchema().orElseThrow(() -> new IllegalArgumentException("Schema required for Kafka tables")), tableHandle.getName());
        deleteTopic(tableDefinition.getTopic());
        createTopic(tableDefinition.getTopic(), tableDefinition.getPartitionsCount(), tableDefinition.getReplicationLevel());
        insertDataIntoTopic(tableDefinition.getTopic(), tableDefinition.getDataSource());
        TableName createdTableName = new TableName(
                tableHandle.getDatabase().orElse(getDatabaseName()),
                tableHandle.getSchema(),
                tableHandle.getName(),
                tableHandle.getName());
        return new KafkaTableInstance(createdTableName, tableDefinition);
    }

    private void verifyTableExistsInPresto(String schema, String name)
    {
        String sql = format("select count(1) from %s.information_schema.tables where table_schema='%s' and table_name='%s'", prestoKafkaCatalog, schema, name);
        QueryResult queryResult = prestoQueryExecutor.executeQuery(sql);
        if ((Long) queryResult.row(0).get(0) != 1) {
            throw new RuntimeException(format("Table %s.%s not defined if kafka catalog (%s)", schema, name, prestoKafkaCatalog));
        }
    }

    private void deleteTopic(String topic)
    {
        AdminClient kafkaAdminClient = KafkaAdminClient.create(getKafkaProperties());

        try {
            ListTopicsResult topics = kafkaAdminClient.listTopics();
            Set<String> names = topics.names().get();

            if (names.contains(topic)) {
                kafkaAdminClient.deleteTopics(ImmutableList.of(topic)).all().get();
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Could not delete topic " + topic, e);
        }
    }

    private void createTopic(String topic, int partitionsCount, int replicationLevel)
    {
        AdminClient kafkaAdminClient = KafkaAdminClient.create(getKafkaProperties());

        try {
            kafkaAdminClient.createTopics(ImmutableList.of(new NewTopic(topic, partitionsCount, toShortExact(replicationLevel)))).all().get();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertDataIntoTopic(String topic, KafkaDataSource dataSource)
    {
        Producer<byte[], byte[]> producer = new KafkaProducer<>(getKafkaProperties());

        Iterator<KafkaMessage> messages = dataSource.getMessages();
        while (messages.hasNext()) {
            KafkaMessage message = messages.next();
            try {
                producer.send(new ProducerRecord<>(
                        topic,
                        message.getPartition().isPresent() ? message.getPartition().getAsInt() : null,
                        message.getKey().orElse(null),
                        message.getValue())).get();
            }
            catch (Exception e) {
                throw new RuntimeException("could not send message to topic " + topic);
            }
        }
    }


    private Properties getKafkaProperties()
    {
        Properties props = new Properties();

        props.put("bootstrap.servers", brokerConfiguration.getStringMandatory("host") + ":" + brokerConfiguration.getIntMandatory("port"));
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        for (String key : brokerConfiguration.listKeys()) {
            if (key.equals("host") || key.equals("port")) {
                continue;
            }
            props.put(key, brokerConfiguration.getStringMandatory(key));
        }
        return props;
    }

    @Override
    public TableInstance<KafkaTableDefinition> createMutable(KafkaTableDefinition tableDefinition, MutableTableRequirement.State state, TableHandle tableHandle)
    {
        throw new IllegalArgumentException("Mutable tables are not supported by KafkaTableManager");
    }

    @Override
    public void dropTable(TableName tableName)
    {
        throw new IllegalArgumentException("dropTable not supported by KafkaTableManager");
    }

    @Override
    public void dropStaleMutableTables()
    {
        // noop
    }

    @Override
    public String getDatabaseName()
    {
        return databaseName;
    }

    @Override
    public Class<? extends TableDefinition> getTableDefinitionClass()
    {
        return KafkaTableDefinition.class;
    }

    private static short toShortExact(int value)
    {
        if (value > Short.MAX_VALUE || value < Short.MIN_VALUE) {
            throw new ArithmeticException("short overflow");
        }

        return (short) value;
    }
}
