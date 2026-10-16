package dev.fizz.wkclimb.assets;

import dev.fizz.wkclimb.assets.DBusWire.Message;
import dev.fizz.wkclimb.assets.DBusWire.Variant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Asks the desktop's file chooser portal (org.freedesktop.portal.FileChooser) for a folder. It is the
 * system's own folder picker, and in a Flatpak the folder picked becomes readable to the sandbox
 * (through the document portal, remembered across restarts), so nothing else needs to be granted.
 *
 * A minimal D-Bus client over the session bus's Unix socket: SASL EXTERNAL, Hello, AddMatch for the
 * request's Response signal, OpenFile, then wait for the Response. Blocking; one picker per instance.
 */
public final class PortalFolderPicker implements AutoCloseable {
	private static final String PORTAL = "org.freedesktop.portal.Desktop";
	private static final String PORTAL_PATH = "/org/freedesktop/portal/desktop";

	public enum Outcome { CHOSEN, CANCELLED }

	public record Result(Outcome outcome, Path folder) {}

	private final SocketChannel channel;
	private final InputStream in;
	private final OutputStream out;
	private final String uniqueName;
	private long serial;
	private volatile String requestHandle;

	private PortalFolderPicker(SocketChannel channel) throws IOException {
		this.channel = channel;
		this.in = Channels.newInputStream(channel);
		this.out = Channels.newOutputStream(channel);
		authenticate();
		uniqueName = (String) call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "Hello", "", List.of()).get(0);
	}

	/** Connects to the session bus; throws if there is none this can use (then use another picker). */
	public static PortalFolderPicker connect() throws IOException {
		Path socket = busSocket(System.getenv("DBUS_SESSION_BUS_ADDRESS"), uid())
				.orElseThrow(() -> new IOException("no usable D-Bus session bus address"));
		SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
		try {
			ch.connect(UnixDomainSocketAddress.of(socket));
			return new PortalFolderPicker(ch);
		} catch (IOException | RuntimeException e) {
			ch.close();
			throw e;
		}
	}

	/**
	 * Shows the folder picker and waits until it closes.
	 * @param startFolder where it opens (a hint the picker may ignore), or null
	 */
	public Result pickFolder(String title, Path startFolder) throws IOException {
		String token = "wkclimb" + Integer.toUnsignedString(ThreadLocalRandom.current().nextInt());
		String expected = requestPath(uniqueName, token);
		addMatch(expected);
		Map<String, Variant> options = new LinkedHashMap<>();
		options.put("handle_token", new Variant("s", token));
		options.put("directory", new Variant("b", true));
		options.put("modal", new Variant("b", true));
		if (startFolder != null) options.put("current_folder", new Variant("ay", nulTerminated(startFolder)));
		String handle = (String) call(PORTAL, PORTAL_PATH, "org.freedesktop.portal.FileChooser", "OpenFile", "ssa{sv}",
				List.of("", title, options)).get(0);
		if (!handle.equals(expected)) addMatch(handle); // portals older than handle_token support
		requestHandle = handle;
		while (true) {
			Message m = DBusWire.read(in);
			if (m.type() == DBusWire.SIGNAL && handle.equals(m.path()) && "Response".equals(m.member())) {
				requestHandle = null;
				return response(m.body());
			}
		}
	}

	/** Reads one setting through the portal (no dialog): a check that the whole connection works. */
	public Object readSetting(String namespace, String key) throws IOException {
		return call(PORTAL, PORTAL_PATH, "org.freedesktop.portal.Settings", "ReadOne", "ss", List.of(namespace, key)).get(0);
	}

	/** Closes an open picker (if any) and the connection; a waiting pickFolder then throws. */
	@Override
	public void close() throws IOException {
		String handle = requestHandle;
		if (handle != null && channel.isOpen()) {
			try {
				send(handle, null, "org.freedesktop.portal.Request", "Close", "", List.of());
			} catch (IOException ignored) {
				// closing anyway
			}
		}
		channel.close();
	}

	@SuppressWarnings("unchecked")
	static Result response(List<Object> body) {
		long code = (Long) body.get(0);
		if (code != 0) return new Result(Outcome.CANCELLED, null);
		Map<Object, Object> results = (Map<Object, Object>) body.get(1);
		Variant uris = (Variant) results.get("uris");
		if (uris == null || ((List<Object>) uris.value()).isEmpty()) return new Result(Outcome.CANCELLED, null);
		return new Result(Outcome.CHOSEN, fileUriToPath((String) ((List<Object>) uris.value()).get(0)));
	}

	static Path fileUriToPath(String uri) {
		return Path.of(URI.create(uri));
	}

	/** Where the portal puts the Response signal for a request made with this token. */
	static String requestPath(String uniqueName, String token) {
		return PORTAL_PATH + "/request/" + uniqueName.substring(1).replace('.', '_') + "/" + token;
	}

	/**
	 * The socket from a bus address like "unix:path=/run/user/1000/bus,guid=..." (several may be
	 * separated by ';'); without an address, the usual /run/user/&lt;uid&gt;/bus.
	 */
	static Optional<Path> busSocket(String address, int uid) {
		if (address == null || address.isBlank()) return Optional.of(Path.of("/run/user/" + uid + "/bus"));
		for (String one : address.split(";")) {
			if (!one.startsWith("unix:")) continue;
			for (String kv : one.substring(5).split(",")) {
				if (kv.startsWith("path=")) return Optional.of(Path.of(unescape(kv.substring(5))));
			}
		}
		return Optional.empty();
	}

	/** D-Bus addresses escape bytes as %XX. */
	static String unescape(String s) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '%' && i + 2 < s.length()) {
				b.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
				i += 2;
			} else {
				b.write(c);
			}
		}
		return b.toString(StandardCharsets.UTF_8);
	}

	private static byte[] nulTerminated(Path p) {
		byte[] path = p.toString().getBytes(StandardCharsets.UTF_8);
		byte[] b = new byte[path.length + 1];
		System.arraycopy(path, 0, b, 0, path.length);
		return b;
	}

	private static int uid() {
		try {
			return (Integer) Files.getAttribute(Path.of("/proc/self"), "unix:uid");
		} catch (IOException | RuntimeException e) {
			return 1000;
		}
	}

	private void authenticate() throws IOException {
		String uidHex = HexFormat.of().formatHex(Integer.toString(uid()).getBytes(StandardCharsets.US_ASCII));
		out.write(0);
		out.write(("AUTH EXTERNAL " + uidHex + "\r\n").getBytes(StandardCharsets.US_ASCII));
		out.flush();
		String reply = readLine();
		if (!reply.startsWith("OK ")) throw new IOException("D-Bus login refused: " + reply);
		out.write("BEGIN\r\n".getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}

	private String readLine() throws IOException {
		StringBuilder sb = new StringBuilder();
		int c;
		while ((c = in.read()) != -1) {
			if (c == '\n') break;
			if (c != '\r') sb.append((char) c);
		}
		return sb.toString();
	}

	private void addMatch(String path) throws IOException {
		call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "AddMatch", "s",
				List.of("type='signal',sender='" + PORTAL + "',interface='org.freedesktop.portal.Request',member='Response',path='" + path + "'"));
	}

	private long send(String path, String destination, String iface, String member, String signature, List<Object> args) throws IOException {
		long s = ++serial;
		out.write(DBusWire.methodCall(s, destination == null ? PORTAL : destination, path, iface, member, signature, args));
		out.flush();
		return s;
	}

	/** Sends a method call and waits for its reply (other messages arriving meanwhile are skipped). */
	private List<Object> call(String destination, String path, String iface, String member, String signature, List<Object> args) throws IOException {
		long s = send(path, destination, iface, member, signature, args);
		while (true) {
			Message m = DBusWire.read(in);
			if (m.replySerial() != s) continue;
			if (m.type() == DBusWire.ERROR) {
				throw new IOException(m.errorName() + (m.body().isEmpty() ? "" : ": " + m.body().get(0)));
			}
			if (m.type() == DBusWire.METHOD_RETURN) return m.body();
		}
	}
}
