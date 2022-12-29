package com.example.tracing

import io.micrometer.context.ContextSnapshot
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
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
	observationRegistry: ObservationRegistry
) {

	val webClient = WebClient.builder()
		.filter(trace("todo.api", observationRegistry))
		.baseUrl("https://jsonplaceholder.typicode.com")
		.build()

	val log = LoggerFactory.getLogger(javaClass)

	@GetMapping("/test")
	suspend fun test(): String {
		// simulate some complex calculation
		delay(1.seconds)

		observeCtx { log.info("test log with tracing info") }

		Mono.deferContextual { contextView ->
			ContextSnapshot.setThreadLocalsFrom(
				contextView,
				ObservationThreadLocalAccessor.KEY
			).use {
				log.info("test log with tracing info")
				Mono.empty<String>()
			}
		}.awaitSingleOrNull()

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

fun trace(name: String, observationRegistry: ObservationRegistry)= ExchangeFilterFunction { clientRequest, next ->
	Mono.deferContextual { contextView ->
		ContextSnapshot.setThreadLocalsFrom(
			contextView,
			ObservationThreadLocalAccessor.KEY
		).use {
			val observation = Observation.start(name, observationRegistry)
			observation.parentObservation(observationRegistry.currentObservation)
			observation.lowCardinalityKeyValue("uri", clientRequest.url().toString())
			observation.lowCardinalityKeyValue("method", clientRequest.method().name())

			Mono.just(observation).flatMap {
				next.exchange(clientRequest)
			}.doOnError {
				observation.error(it)
			}.doFinally {
				observation.stop()
			}.doOnSuccess {
				observation.highCardinalityKeyValue("status", it.statusCode().value().toString())
				observation.lowCardinalityKeyValue("outcome", when {
					it.statusCode().is2xxSuccessful -> "SUCCESS"
					it.statusCode().is4xxClientError -> "CLIENT ERROR"
					it.statusCode().is5xxServerError -> "SERVER ERROR"
					else -> "OTHER ERROR"
				})
			}
		}
	}
}
