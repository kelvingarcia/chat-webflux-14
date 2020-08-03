package com.example.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatApplication.class, args);
	}

}

@Configuration
class ChatRouter{

	@Bean
	RouterFunction<ServerResponse> routes(ChatService chatService){
		return route()
			.GET("/mensagens-stream/{name}", req -> ok().contentType(MediaType.APPLICATION_STREAM_JSON).body(
					chatService.mensagemStream(req.pathVariable("name")),
					Mensagem.class
			))
			.GET("/mensagens-teste", req -> ok().contentType(MediaType.TEXT_EVENT_STREAM).body(
					chatService.mensagemEstatico(),
					Mensagem.class
			))
			.POST("/mensagem", req -> ok().body(
					req.bodyToMono(MensagemDto.class).flatMap(m -> chatService.mandaMensagem(m)),
					Mensagem.class
			))
			.GET("/mensagens", req -> ok().body(chatService.getAllMensagens(), Mensagem.class))
			.GET("/mensagens-live", req -> ok().contentType(MediaType.APPLICATION_STREAM_JSON).body(
					chatService.getMensagensSink(),
					Mensagem.class
			))
			.build();
	}

}

@Service
class ChatService{

	private Flux<Mensagem> mensagens = Flux.empty();
	private Flux<ConsumerFluxSink> consumerFluxSinkFlux = Flux.empty();

	Flux<Mensagem> getMensagensSink(){
		var consumerLocal = new ConsumerFluxSink();
		this.consumerFluxSinkFlux = this.consumerFluxSinkFlux.concatWithValues(consumerLocal);
		return Flux.concat(
				this.mensagens,
				Flux.create(consumerLocal)
		);
	}

	Flux<Mensagem> getAllMensagens(){
		return this.mensagens;
	}

	Mono<Mensagem> mandaMensagem(MensagemDto mensagemDto){
		var mensagem = new Mensagem(
				mensagemDto.nome(),
				mensagemDto.mensagem(),
				Instant.now()
		);
		this.mensagens = this.mensagens.concatWithValues(mensagem);
		this.consumerFluxSinkFlux.subscribe(x -> x.publishMensagem(mensagem));
		return Mono.just(mensagem);
	}

	Flux<Mensagem> mensagemStream(String name){
		return Flux.fromStream(
				Stream.generate(() -> new Mensagem(name, "Olá", Instant.now()))
		).delayElements(Duration.ofSeconds(3));
	}

	Flux<Mensagem> mensagemEstatico(){
		return Flux.just(
				new Mensagem("Kevin", "Olá", Instant.now()),
				new Mensagem("Garcia", "Olá", Instant.now()),
				new Mensagem("Rodrigues", "Olá", Instant.now()),
				new Mensagem("Batista", "Olá", Instant.now())
		).delayElements(Duration.ofSeconds(2));
	}
}

class ConsumerFluxSink implements Consumer<FluxSink<Mensagem>> {

	private FluxSink<Mensagem> fluxSink;

	@Override
	public void accept(FluxSink<Mensagem> mensagemFluxSink) {
		this.fluxSink = mensagemFluxSink;
	}

	public void publishMensagem(Mensagem mensagem){
		this.fluxSink.next(mensagem);
	}
}

record MensagemDto(String nome, String mensagem){
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Mensagem {
	private String usuario;
	private String texto;
	private Instant horario;
}
