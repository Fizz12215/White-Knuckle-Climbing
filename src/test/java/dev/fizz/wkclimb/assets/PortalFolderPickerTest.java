package dev.fizz.wkclimb.assets;

import dev.fizz.wkclimb.assets.DBusWire.Message;
import dev.fizz.wkclimb.assets.DBusWire.Variant;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The D-Bus wire format and the portal's request and response handling, without a bus. */
class PortalFolderPickerTest {
	@Test
	void signatures() {
		assertEquals(List.of("s", "s", "a{sv}"), DBusWire.splitSignature("ssa{sv}"));
		assertEquals(List.of("u", "a{sv}"), DBusWire.splitSignature("ua{sv}"));
		assertEquals(List.of("a(yv)", "aay", "(sa(us))"), DBusWire.splitSignature("a(yv)aay(sa(us))"));
	}

	@Test
	void openFileCallRoundTrip() throws IOException {
		Map<String, Variant> options = new LinkedHashMap<>();
		options.put("handle_token", new Variant("s", "wkclimb42"));
		options.put("directory", new Variant("b", true));
		options.put("current_folder", new Variant("ay", "/home/me\0".getBytes(StandardCharsets.UTF_8)));
		byte[] bytes = DBusWire.methodCall(3, "org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop",
				"org.freedesktop.portal.FileChooser", "OpenFile", "ssa{sv}", List.of("", "Choose", options));
		Message m = DBusWire.read(new ByteArrayInputStream(bytes));
		assertEquals(DBusWire.METHOD_CALL, m.type());
		assertEquals(3, m.serial());
		assertEquals("/org/freedesktop/portal/desktop", m.path());
		assertEquals("org.freedesktop.portal.FileChooser", m.iface());
		assertEquals("OpenFile", m.member());
		assertEquals("ssa{sv}", m.signature());
		assertEquals("", m.body().get(0));
		assertEquals("Choose", m.body().get(1));
		@SuppressWarnings("unchecked")
		Map<Object, Object> got = (Map<Object, Object>) m.body().get(2);
		assertEquals(new Variant("s", "wkclimb42"), got.get("handle_token"));
		assertEquals(new Variant("b", true), got.get("directory"));
		assertArrayEquals("/home/me\0".getBytes(StandardCharsets.UTF_8), (byte[]) ((Variant) got.get("current_folder")).value());
	}

	@Test
	void responseSignal() throws IOException {
		// a Response signal as the portal sends it: (u response, a{sv} results) with results["uris"] = as
		Map<String, Variant> results = new LinkedHashMap<>();
		results.put("uris", new Variant("as", List.of("file:///run/user/1000/doc/abc123/White%20Knuckle")));
		results.put("writable", new Variant("b", false));
		byte[] bytes = DBusWire.methodCall(9, null, "/org/freedesktop/portal/desktop/request/1_42/wkclimb7",
				"org.freedesktop.portal.Request", "Response", "ua{sv}", List.of(0L, results));
		bytes[1] = DBusWire.SIGNAL;
		Message m = DBusWire.read(new ByteArrayInputStream(bytes));
		assertEquals(DBusWire.SIGNAL, m.type());
		assertEquals("Response", m.member());
		PortalFolderPicker.Result r = PortalFolderPicker.response(m.body());
		assertEquals(PortalFolderPicker.Outcome.CHOSEN, r.outcome());
		assertEquals(Path.of("/run/user/1000/doc/abc123/White Knuckle"), r.folder());

		assertEquals(PortalFolderPicker.Outcome.CANCELLED, PortalFolderPicker.response(List.of(1L, Map.of())).outcome());
	}

	@Test
	void bigEndianMessagesAreRead() {
		// "org.freedesktop.DBus.Hello" reply from a big-endian peer: header + body "s" ":1.5"
		byte[] be = {
				'B', 2, 1, 1, 0, 0, 0, 9, 0, 0, 0, 1, 0, 0, 0, 15,
				5, 1, 'u', 0, 0, 0, 0, 1, 8, 1, 'g', 0, 1, 's', 0, 0,
				0, 0, 0, 4, ':', '1', '.', '5', 0 };
		Message m = DBusWire.parse(be);
		assertEquals(DBusWire.METHOD_RETURN, m.type());
		assertEquals(1, m.replySerial());
		assertEquals(List.of(":1.5"), m.body());
	}

	@Test
	void requestPathAndBusAddress() {
		assertEquals("/org/freedesktop/portal/desktop/request/1_42/wkclimb7", PortalFolderPicker.requestPath(":1.42", "wkclimb7"));
		assertEquals(Optional.of(Path.of("/run/flatpak/bus")), PortalFolderPicker.busSocket("unix:path=/run/flatpak/bus", 1000));
		assertEquals(Optional.of(Path.of("/run/user/1000/bus")), PortalFolderPicker.busSocket("unix:path=/run/user/1000/bus,guid=abc", 1000));
		assertEquals(Optional.of(Path.of("/tmp/my bus")), PortalFolderPicker.busSocket("tcp:host=x;unix:path=/tmp/my%20bus", 1000));
		assertEquals(Optional.of(Path.of("/run/user/1000/bus")), PortalFolderPicker.busSocket(null, 1000));
		assertEquals(Optional.empty(), PortalFolderPicker.busSocket("unix:abstract=/tmp/dbus-x", 1000));
	}
}
