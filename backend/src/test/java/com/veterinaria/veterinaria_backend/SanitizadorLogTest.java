package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.security.SanitizadorLog;

class SanitizadorLogTest {

	@Test
	@DisplayName("Anti Log Injection: neutraliza saltos de linea y caracteres de control")
	void previeneLogInjection() {
		String malicioso = "admin\r\n2026-11-19 ERROR [fake] Injected log line\t\0";
		String limpio = SanitizadorLog.limpiarControl(malicioso);

		assertThat(limpio).doesNotContain("\r");
		assertThat(limpio).doesNotContain("\n");
		assertThat(limpio).doesNotContain("\t");
		assertThat(limpio).doesNotContain("\0");
	}

	@Test
	@DisplayName("Anti PII: enmascara nombres de usuario y correos electronicos")
	void enmascaraUsuariosYEmails() {
		assertThat(SanitizadorLog.sanitizarUsuario("duenio")).isEqualTo("d***o");
		assertThat(SanitizadorLog.sanitizarUsuario("ana")).isEqualTo("a***a");
		assertThat(SanitizadorLog.sanitizarUsuario("ab")).isEqualTo("**");
		assertThat(SanitizadorLog.sanitizarUsuario("")).isEqualTo("anonimo");
		assertThat(SanitizadorLog.sanitizarUsuario(null)).isEqualTo("anonimo");

		// Email: conserva el dominio pero enmascara la parte local
		assertThat(SanitizadorLog.sanitizarUsuario("veterinario@clinica.com")).isEqualTo("v***o@clinica.com");
		assertThat(SanitizadorLog.sanitizarUsuario("a@clinica.com")).isEqualTo("*@clinica.com");
	}

	@Test
	@DisplayName("Anti PII: enmascara ultimo segmento de IPv4 e IPv6")
	void enmascaraDireccionesIp() {
		assertThat(SanitizadorLog.sanitizarIp("192.168.1.100")).isEqualTo("192.168.1.***");
		assertThat(SanitizadorLog.sanitizarIp("10.0.0.1")).isEqualTo("10.0.0.***");
		assertThat(SanitizadorLog.sanitizarIp("127.0.0.1")).isEqualTo("127.0.0.***");
		assertThat(SanitizadorLog.sanitizarIp("2001:db8::1")).isEqualTo("2001:db8::***");
		assertThat(SanitizadorLog.sanitizarIp("::1")).isEqualTo("::***");
		assertThat(SanitizadorLog.sanitizarIp(null)).isEqualTo("desconocida");
		assertThat(SanitizadorLog.sanitizarIp("")).isEqualTo("desconocida");
	}

	@Test
	@DisplayName("Sanitiza claves compuestas de rate limiting")
	void sanitizaClaveCompuesta() {
		String clave = "duenio\r\n|192.168.1.50";
		String sanitizada = SanitizadorLog.sanitizarParaLog(clave);

		assertThat(sanitizada).doesNotContain("\r");
		assertThat(sanitizada).doesNotContain("\n");
		assertThat(sanitizada).isEqualTo("d***o|192.168.1.***");
	}

	@Test
	@DisplayName("Oculta passwords en URLs de Redis")
	void ocultaCredencialesEnUrl() {
		String conPassword = "redis://:miSuperPasswordSecreto@redis-host.com:6379/0";
		String sanitizada = SanitizadorLog.sanitizarUrl(conPassword);

		assertThat(sanitizada).doesNotContain("miSuperPasswordSecreto");
		assertThat(sanitizada).contains("***");
	}

	@Test
	@DisplayName("Oculta credenciales aunque intenten inyectar saltos de linea en la URL")
	void ocultaCredencialesConLogInjection() {
		String maliciosa = "redis://:clave\r\n2026-11-19 ERROR falso@redis-host.com:6379/0";
		String sanitizada = SanitizadorLog.sanitizarUrl(maliciosa);

		assertThat(sanitizada).doesNotContain("clave");
		assertThat(sanitizada).doesNotContain("\r");
		assertThat(sanitizada).doesNotContain("\n");
		assertThat(sanitizada).contains("***");
	}
}
