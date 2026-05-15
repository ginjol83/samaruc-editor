package com.retroeditor.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.plugin.PluginInfo;
import com.retroeditor.plugin.PluginManager;

/**
 * Pruebas unitarias de {@link PluginApplicationService}.
 *
 * <p>Estas pruebas cubren operaciones de infraestructura de plugins sin JavaFX:
 * creacion de carpeta, copia de JAR, descubrimiento seguro cuando no hay manager
 * y delegacion de habilitado/deshabilitado con recarga.
 */
class PluginApplicationServiceTest {

	private final PluginApplicationService service = new PluginApplicationService();

	@TempDir
	Path tempDir;

	/**
	 * Escenario: la carpeta de plugins aun no existe en disco.
	 *
	 * <p>Verifica que el servicio la crea de forma idempotente y que el resultado
	 * final es un directorio valido.
	 */
	@Test
	void ensurePluginsDirCreatesFolder() {
		File dir = tempDir.resolve("plugins").toFile();
		Assertions.assertFalse(dir.exists());

		service.ensurePluginsDir(dir);

		Assertions.assertTrue(dir.exists());
		Assertions.assertTrue(dir.isDirectory());
	}

	/**
	 * Escenario: instalacion de un JAR de plugin en una carpeta destino.
	 *
	 * <p>Confirma que el archivo se copia fisicamente y que su contenido no cambia
	 * durante la operacion de instalacion.
	 */
	@Test
	void installPluginJarCopiesFile() throws Exception {
		Path source = tempDir.resolve("demo.jar");
		Files.writeString(source, "jar-content");
		File pluginsDir = tempDir.resolve("plugins").toFile();

		File copied = service.installPluginJar(source.toFile(), pluginsDir);

		Assertions.assertTrue(copied.exists());
		Assertions.assertEquals("jar-content", Files.readString(copied.toPath()));
	}

	/**
	 * Escenario defensivo: el manager de plugins es {@code null}.
	 *
	 * <p>El servicio no debe lanzar excepciones y debe devolver una lista vacia
	 * para simplificar el consumo desde la capa superior.
	 */
	@Test
	void discoverPluginsReturnsEmptyWhenManagerIsNull() {
		List<PluginInfo> info = service.discoverPlugins(null);
		Assertions.assertTrue(info.isEmpty());
	}

	/**
	 * Escenario: habilitar o deshabilitar un plugin identificado por su JAR.
	 *
	 * <p>Verifica doble delegacion: primero cambia el estado con
	 * {@code setJarEnabled(...)} y despues solicita una unica recarga con
	 * {@code reloadPlugins()}.
	 */
	@Test
	void setPluginEnabledAndReloadInvokesManager() {
		FakePluginManager fake = new FakePluginManager();

		service.setPluginEnabledAndReload(fake, "demo.jar", true);

		Assertions.assertEquals("demo.jar", fake.lastJarName);
		Assertions.assertTrue(fake.lastEnabled);
		Assertions.assertEquals(1, fake.reloadCalls);
	}

	/**
	 * Escenario: eliminacion de un JAR existente en la carpeta de plugins.
	 *
	 * <p>Verifica que la operacion devuelve {@code true} y el archivo desaparece
	 * del sistema de archivos.
	 */
	@Test
	void deletePluginJarRemovesExistingFile() throws Exception {
		File pluginsDir = tempDir.resolve("plugins").toFile();
		service.ensurePluginsDir(pluginsDir);
		Path jarPath = pluginsDir.toPath().resolve("demo.jar");
		Files.writeString(jarPath, "content");

		boolean deleted = service.deletePluginJar(pluginsDir, "demo.jar");

		Assertions.assertTrue(deleted);
		Assertions.assertFalse(Files.exists(jarPath));
	}

	/**
	 * Escenario: se intenta eliminar un JAR inexistente.
	 *
	 * <p>El servicio responde {@code false} sin lanzar excepcion para facilitar
	 * manejo defensivo desde la UI.
	 */
	@Test
	void deletePluginJarReturnsFalseWhenFileIsMissing() {
		File pluginsDir = tempDir.resolve("plugins").toFile();
		service.ensurePluginsDir(pluginsDir);

		boolean deleted = service.deletePluginJar(pluginsDir, "missing.jar");

		Assertions.assertFalse(deleted);
	}

	/**
	 * Doble de pruebas para capturar llamadas al manager sin cargar plugins reales.
	 *
	 * <p>Evita dependencias de IO/carga dinamica y permite afirmar orden y datos
	 * de invocacion de manera determinista.
	 */
	private static class FakePluginManager extends PluginManager {
		/** Ultimo nombre de JAR recibido por setJarEnabled. */
		String lastJarName;
		/** Ultimo estado de habilitado recibido por setJarEnabled. */
		boolean lastEnabled;
		/** Numero de veces que se invoco reloadPlugins. */
		int reloadCalls;

		FakePluginManager() {
			super(null, null, null);
		}

		@Override
		public void setJarEnabled(String jarName, boolean enabled) {
			this.lastJarName = jarName;
			this.lastEnabled = enabled;
		}

		@Override
		public void reloadPlugins() {
			reloadCalls++;
		}

		@Override
		public List<PluginInfo> discoverPlugins() {
			PluginInfo info = new PluginInfo();
			info.jarName = "demo.jar";
			info.enabled = true;
			List<PluginInfo> list = new ArrayList<>();
			list.add(info);
			return list;
		}
	}
}


