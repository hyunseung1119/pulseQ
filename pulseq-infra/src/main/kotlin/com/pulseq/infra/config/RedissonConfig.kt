package com.pulseq.infra.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig(
    @Value("\${spring.data.redis.host:localhost}") private val host: String,
    @Value("\${spring.data.redis.port:6379}") private val port: Int,
    @Value("\${spring.data.redis.password:}") private val password: String
) {
    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        val address = "redis://$host:$port"
        config.useSingleServer()
            .setAddress(address)
            .setConnectTimeout(10000)
            .setTimeout(5000)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .also {
                if (password.isNotBlank()) {
                    it.setPassword(password)
                }
            }
        return Redisson.create(config)
    }
}
