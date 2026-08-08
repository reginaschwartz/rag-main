import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Reads the topic as part of a consumer group and commits offsets after processing. */
public class KafkaConsumerDemo {

    public static void main(String[] args) {
        String bootstrapServers = args.length > 0 ? args[0] : "localhost:9092";

        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-processors");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // a brand new group starts at the beginning
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);     // commit only once the work is done

        try (Consumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(KafkaProducerDemo.TOPIC));

            int idlePolls = 0;
            while (idlePolls < 3) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) {
                    idlePolls++;
                    continue;
                }
                idlePolls = 0;

                for (ConsumerRecord<String, String> record : records) {
                    System.out.printf("received %-8s key=%-11s partition %d, offset %d%n",
                            record.value(), record.key(), record.partition(), record.offset());
                }
                // At-least-once: the work above already happened, so a crash before this line means
                // the batch is redelivered rather than lost.
                consumer.commitSync();
            }
            System.out.println("no more records, stopping");
        }
    }
}
