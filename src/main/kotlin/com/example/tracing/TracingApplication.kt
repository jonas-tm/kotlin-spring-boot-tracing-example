package com.example.tracing

import io.micrometer.context.ContextSnapshot
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.time.Duration.Companion.seconds

@SpringBootApplication
class TracingApplication

fun main(args: Array<String>) {
	runApplication<TracingApplication>(*args)
}

@RestController
class Controller(
	webClientBuilder: WebClient.Builder
) {

	val webClient = webClientBuilder
		.baseUrl("https://jsonplaceholder.typicode.com")
		.build()

	val log = LoggerFactory.getLogger(javaClass)

	@GetMapping("/test")
	suspend fun test(): String {
		// simulate some complex calculation
		delay(1.seconds)

		observeCtx { log.info("test log with tracing info") }

		// make web client call and return response
		return webClient.get()
			.uri("/todos/1")
			.retrieve()
			.bodyToMono(String::class.java)
			.awaitSingle();
	}
}

suspend inline fun observeCtx(crossinline f: () -> Unit) {
	Mono.deferContextual { contextView ->
		ContextSnapshot.setThreadLocalsFrom(
			contextView,
			ObservationThreadLocalAccessor.KEY
		).use {
			f()
			Mono.empty<Unit>()
		}
	}.awaitSingleOrNull()
}
