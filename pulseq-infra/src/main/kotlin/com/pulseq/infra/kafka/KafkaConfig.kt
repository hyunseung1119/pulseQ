package com.pulseq.infra.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") private val bootstrapServers: String
) {

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "1",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.LINGER_MS_CONFIG to 10,
            ProducerConfig.BATCH_SIZE_CONFIG to 16384
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory)
    }

    @Bean
    fun queueEventsTopic(): NewTopic =
        TopicBuilder.name(TOPIC_QUEUE_EVENTS)
            .partitions(6)
            .replicas(1)
            .config("retention.ms", "604800000") // 7일
            .build()

    @Bean
    fun botEventsTopic(): NewTopic =
        TopicBuilder.name(TOPIC_BOT_EVENTS)
            .partitions(3)
            .replicas(1)
            .config("retention.ms", "604800000")
            .build()

    @Bean
    fun metricsTopic(): NewTopic =
        TopicBuilder.name(TOPIC_METRICS)
            .partitions(3)
            .replicas(1)
            .config("retention.ms", "86400000") // 1일
            .build()

    companion object {
        const val TOPIC_QUEUE_EVENTS = "pulseq.queue.events"
        const val TOPIC_BOT_EVENTS = "pulseq.bot.events"
        const val TOPIC_METRICS = "pulseq.metrics"
    }
}
