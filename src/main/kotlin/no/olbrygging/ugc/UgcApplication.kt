package no.olbrygging.ugc

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class UgcApplication

fun main(args: Array<String>) {
    runApplication<UgcApplication>(*args)
}
