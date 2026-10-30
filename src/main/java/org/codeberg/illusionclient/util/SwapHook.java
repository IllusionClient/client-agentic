package org.codeberg.illusionclient.util;

import org.codeberg.illusionclient.IllusionClient;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class SwapHook {
	private static final Linker LINKER = Linker.nativeLinker();
	private static final Arena ARENA = Arena.global();
	private static final int PATCH_SIZE = 14;

	private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
	private static final boolean WINDOWS = OS.startsWith("windows");
	public static final boolean SUPPORTED = (WINDOWS || OS.startsWith("linux"))
		&& (System.getProperty("os.arch").equals("amd64") || System.getProperty("os.arch").equals("x86_64"));

	private static Boolean installed;
	private static MethodHandle original;
	private static FileChannel memory;
	private static Patch glx, egl;

	private SwapHook() {
	}

	public static boolean install() {
		if (installed != null) return installed;
		installed = false;
		if (!SUPPORTED) return false;

		try {
			if (WINDOWS) hookWindows();
			else hookLinux();
			installed = true;
		} catch (Throwable throwable) {
			IllusionClient.LOGGER.error("Failed to hook buffer swap", throwable);
		}
		return installed;
	}

	private static void hookWindows() throws Throwable {
		Path dll = Files.createTempFile("illusion-minhook", ".dll");
		dll.toFile().deleteOnExit();
		try (InputStream in = SwapHook.class.getResourceAsStream("/natives/MinHook.x64.dll")) {
			if (in == null) throw new IOException("MinHook.x64.dll is missing from the jar");
			Files.copy(in, dll, StandardCopyOption.REPLACE_EXISTING);
		}

		SymbolLookup minHook = SymbolLookup.libraryLookup(dll, ARENA);
		MemorySegment swap = SymbolLookup.libraryLookup("opengl32", ARENA).findOrThrow("wglSwapBuffers");
		MemorySegment detour = upcall("wglSwapBuffers", FunctionDescriptor.of(JAVA_INT, ADDRESS));
		MemorySegment trampoline = ARENA.allocate(ADDRESS);

		MethodHandle initialize = downcall(minHook, "MH_Initialize", FunctionDescriptor.of(JAVA_INT));
		MethodHandle create = downcall(minHook, "MH_CreateHook", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
		MethodHandle enable = downcall(minHook, "MH_EnableHook", FunctionDescriptor.of(JAVA_INT, ADDRESS));

		check("MH_Initialize", (int) initialize.invokeExact());
		check("MH_CreateHook", (int) create.invokeExact(swap, detour, trampoline));
		original = LINKER.downcallHandle(trampoline.get(ADDRESS, 0), FunctionDescriptor.of(JAVA_INT, ADDRESS));
		check("MH_EnableHook", (int) enable.invokeExact(swap));
		IllusionClient.LOGGER.info("Hooked wglSwapBuffers in opengl32");
	}

	private static int wglSwapBuffers(MemorySegment hdc) {
		HiddenLayer.composite();
		try {
			return (int) original.invokeExact(hdc);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
	}

	private static void hookLinux() throws Throwable {
		Path glxLibrary = loaded("libGLX.so");
		if (glxLibrary == null) glxLibrary = loaded("libGL.so.1");
		Path eglLibrary = loaded("libEGL.so");
		if (glxLibrary == null && eglLibrary == null) throw new IOException("Neither GLX nor EGL is loaded");

		memory = FileChannel.open(Path.of("/proc/self/mem"), StandardOpenOption.READ, StandardOpenOption.WRITE);
		if (glxLibrary != null) glx = new Patch(glxLibrary, "glXSwapBuffers", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
		if (eglLibrary != null) egl = new Patch(eglLibrary, "eglSwapBuffers", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
	}

	private static void glXSwapBuffers(MemorySegment display, MemorySegment drawable) {
		HiddenLayer.composite();
		try {
			glx.restore();
			glx.original.invokeExact(display, drawable);
			glx.apply();
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
	}

	private static int eglSwapBuffers(MemorySegment display, MemorySegment surface) {
		HiddenLayer.composite();
		try {
			egl.restore();
			int result = (int) egl.original.invokeExact(display, surface);
			egl.apply();
			return result;
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
	}

	private static Path loaded(String name) throws IOException {
		for (String line : Files.readAllLines(Path.of("/proc/self/maps"))) {
			int slash = line.indexOf('/');
			if (slash < 0) continue;

			Path path = Path.of(line.substring(slash));
			if (path.getFileName().toString().startsWith(name)) return path;
		}
		return null;
	}

	private static long base(Path library) throws IOException {
		for (String line : Files.readAllLines(Path.of("/proc/self/maps"))) {
			String[] fields = line.split("\\s+", 6);
			if (fields.length == 6 && fields[5].equals(library.toString()) && Long.parseLong(fields[2], 16) == 0) {
				return Long.parseUnsignedLong(fields[0].substring(0, fields[0].indexOf('-')), 16);
			}
		}
		throw new IOException("No base mapping for " + library);
	}

	private static long[] dynamicSymbol(Path library, String name) throws IOException {
		ByteBuffer elf = ByteBuffer.wrap(Files.readAllBytes(library)).order(ByteOrder.LITTLE_ENDIAN);
		long sections = elf.getLong(0x28);
		int sectionSize = elf.getShort(0x3A);
		int sectionCount = elf.getShort(0x3C);

		for (int i = 0; i < sectionCount; i++) {
			int section = (int) (sections + (long) i * sectionSize);
			if (elf.getInt(section + 4) != 11) continue;

			int symbols = (int) elf.getLong(section + 0x18);
			int count = (int) (elf.getLong(section + 0x20) / elf.getLong(section + 0x38));
			int strings = (int) elf.getLong((int) (sections + (long) elf.getInt(section + 0x28) * sectionSize) + 0x18);

			for (int j = 0; j < count; j++) {
				int symbol = symbols + j * 24;
				if (name.equals(string(elf, strings + elf.getInt(symbol)))) {
					return new long[]{elf.getLong(symbol + 8), elf.getLong(symbol + 16)};
				}
			}
		}
		throw new IOException(name + " not found in " + library);
	}

	private static String string(ByteBuffer elf, int offset) {
		int end = offset;
		while (elf.get(end) != 0) end++;
		return new String(elf.array(), offset, end - offset);
	}

	private static MemorySegment upcall(String name, FunctionDescriptor descriptor) throws ReflectiveOperationException {
		MethodType type = descriptor.toMethodType();
		MethodHandle handle = MethodHandles.lookup().findStatic(SwapHook.class, name, type);
		return LINKER.upcallStub(handle, descriptor, ARENA);
	}

	private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
		return LINKER.downcallHandle(lookup.findOrThrow(name), descriptor);
	}

	private static void check(String function, int status) throws IOException {
		if (status != 0) throw new IOException(function + " returned " + status);
	}

	private static final class Patch {
		final long target;
		final MethodHandle original;
		final ByteBuffer saved, jump;

		Patch(Path library, String function, FunctionDescriptor descriptor) throws Throwable {
			long[] symbol = dynamicSymbol(library, function);
			if (symbol[1] < PATCH_SIZE) throw new IOException(function + " in " + library + " is too small to patch");
			target = base(library) + symbol[0];

			MemorySegment detour = upcall(function, descriptor);
			original = LINKER.downcallHandle(MemorySegment.ofAddress(target), descriptor);
			saved = ByteBuffer.wrap(MemorySegment.ofAddress(target).reinterpret(PATCH_SIZE).toArray(ValueLayout.JAVA_BYTE));
			jump = ByteBuffer.allocate(PATCH_SIZE).order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0xFF).put((byte) 0x25).putInt(0)
				.putLong(detour.address())
				.flip();
			apply();
			IllusionClient.LOGGER.info("Hooked {} in {}", function, library);
		}

		void apply() throws IOException {
			memory.write(jump.rewind(), target);
		}

		void restore() throws IOException {
			memory.write(saved.rewind(), target);
		}
	}
}
