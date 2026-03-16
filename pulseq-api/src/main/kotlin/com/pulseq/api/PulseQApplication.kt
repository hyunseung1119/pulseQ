package com.pulseq.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.pulseq"])
class PulseQApplication

fun main(args: Array<String>) {
    runApplication<PulseQApplication>(*args)
}
