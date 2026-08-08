import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

/** Publishes a handful of records to a topic. */
public class KafkaProducerDemo {

    static final String TOPIC = "orders";

    public static void main(String[] args) throws Exception {
        String bootstrapServers = args.length > 0 ? args[0] : "localhost:9092";

        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.ACKS_CONFIG, "all");              // every in-sync replica must store it
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // a retry cannot duplicate a record
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);             // brief wait so records batch together

        try (Producer<String, String> producer = new KafkaProducer<>(config)) {
            for (int i = 1; i <= 9; i++) {
                // The key decides the partition, so all orders of one customer stay in order.
                String key = "customer-" + (i % 3);
                ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, "order-" + i);

                // get() makes the send synchronous, which is convenient for a demo; production code
                // usually passes a callback and keeps going.
                RecordMetadata metadata = producer.send(record).get();

                System.out.printf("sent %-8s key=%-11s -> partition %d, offset %d%n",
                        record.value(), key, metadata.partition(), metadata.offset());
            }
        }
    }
}
