package com.example.tracing

import io.micrometer.context.ContextSnapshot
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
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
	val observationRegistry: ObservationRegistry,
	val todoRepo: ToDoRepository,
	webClientBuilder: WebClient.Builder
) {

	val webClient = webClientBuilder
		.baseUrl("https://jsonplaceholder.typicode.com")
		.build()

	val log = LoggerFactory.getLogger(javaClass)

	@GetMapping("/test")
	suspend fun test(): String {
		observeCtx {
			val currentObservation = observationRegistry.currentObservation
			currentObservation?.highCardinalityKeyValue("test_key", "test sample value")
			log.info("test log with tracing info")
		}

		runObserved("delay", observationRegistry) {
			delay(1.seconds)
		}

		// Sample traced DB call
		val list = todoRepo.findAll().toList()


		// make web client call and return response
		val todos =  webClient.get()
			.uri("/todos/1")
			.retrieve()
			.bodyToMono(String::class.java)
			.awaitSingle()

		return "${list.size} $todos"
	}
}

@Table("todo")
data class ToDo(
	@Id
	val id: Long = 0,
	val title: String,
)

interface ToDoRepository : CoroutineCrudRepository<ToDo, Long>

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

suspend fun runObserved(name: String, observationRegistry: ObservationRegistry, f: suspend () -> Unit) {
	Mono.deferContextual { contextView ->
		ContextSnapshot.setThreadLocalsFrom(
			contextView,
			ObservationThreadLocalAccessor.KEY
		).use {
			val observation = Observation.start(name, observationRegistry)
			Mono.just(observation).flatMap {
				mono { f() }
			}.doOnError {
				observation.error(it)
				observation.stop()
			}.doOnSuccess {
				observation.stop()
			}
		}
	}.awaitSingleOrNull()
}
