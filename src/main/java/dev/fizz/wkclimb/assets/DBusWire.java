package dev.fizz.wkclimb.assets;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough of the D-Bus wire format for talking to the desktop portal: method calls go out
 * (little-endian), replies, errors and signals come in (either byte order).
 *
 * Values map to Java as: y b n q i u x t -> Long/Boolean, d -> Double, s o g -> String,
 * ay -> byte[], a{..} -> Map, other arrays and structs -> List, v -> {@link Variant}.
 */
final class DBusWire {
	static final int METHOD_CALL = 1, METHOD_RETURN = 2, ERROR = 3, SIGNAL = 4;
	private static final int F_PATH = 1, F_INTERFACE = 2, F_MEMBER = 3, F_ERROR_NAME = 4, F_REPLY_SERIAL = 5,
			F_DESTINATION = 6, F_SENDER = 7, F_SIGNATURE = 8;

	record Variant(String signature, Object value) {}

	record Message(int type, long serial, long replySerial, String path, String iface, String member,
			String errorName, String sender, String signature, List<Object> body) {}

	private DBusWire() {}

	// ---- writing ----

	static byte[] methodCall(long serial, String destination, String path, String iface, String member, String signature, List<Object> args) {
		Writer body = new Writer();
		writeAll(body, signature, args);
		Writer msg = new Writer();
		msg.u8('l');
		msg.u8(METHOD_CALL);
		msg.u8(0);
		msg.u8(1);
		msg.u32(body.size());
		msg.u32(serial);
		List<Object> fields = new ArrayList<>();
		fields.add(List.of((long) F_PATH, new Variant("o", path)));
		if (iface != null) fields.add(List.of((long) F_INTERFACE, new Variant("s", iface)));
		fields.add(List.of((long) F_MEMBER, new Variant("s", member)));
		if (destination != null) fields.add(List.of((long) F_DESTINATION, new Variant("s", destination)));
		if (!signature.isEmpty()) fields.add(List.of((long) F_SIGNATURE, new Variant("g", signature)));
		msg.value("a(yv)", fields);
		msg.align(8);
		msg.bytes(body.toArray());
		return msg.toArray();
	}

	static void writeAll(Writer w, String signature, List<Object> args) {
		List<String> types = splitSignature(signature);
		if (types.size() != args.size()) throw new IllegalArgumentException(signature + " needs " + types.size() + " values");
		for (int i = 0; i < types.size(); i++) w.value(types.get(i), args.get(i));
	}

	/** A growing buffer whose alignment is counted from its start (a message, or a body: both 8-aligned). */
	static final class Writer {
		private byte[] buf = new byte[256];
		private int size;

		int size() {
			return size;
		}

		byte[] toArray() {
			return Arrays.copyOf(buf, size);
		}

		private void ensure(int more) {
			if (size + more > buf.length) buf = Arrays.copyOf(buf, Math.max(buf.length * 2, size + more));
		}

		void u8(int v) {
			ensure(1);
			buf[size++] = (byte) v;
		}

		void bytes(byte[] b) {
			ensure(b.length);
			System.arraycopy(b, 0, buf, size, b.length);
			size += b.length;
		}

		void align(int n) {
			while (size % n != 0) u8(0);
		}

		void u32(long v) {
			align(4);
			for (int i = 0; i < 4; i++) u8((int) (v >>> (8 * i)));
		}

		void u64(long v) {
			align(8);
			for (int i = 0; i < 8; i++) u8((int) (v >>> (8 * i)));
		}

		private void putU32(int at, long v) {
			for (int i = 0; i < 4; i++) buf[at + i] = (byte) (v >>> (8 * i));
		}

		void string(String s) {
			byte[] b = s.getBytes(StandardCharsets.UTF_8);
			u32(b.length);
			bytes(b);
			u8(0);
		}

		void signatureString(String s) {
			byte[] b = s.getBytes(StandardCharsets.US_ASCII);
			u8(b.length);
			bytes(b);
			u8(0);
		}

		@SuppressWarnings("unchecked")
		void value(String sig, Object v) {
			char c = sig.charAt(0);
			switch (c) {
				case 'y' -> u8(((Number) v).intValue());
				case 'b' -> u32((Boolean) v ? 1 : 0);
				case 'n', 'q' -> {
					align(2);
					int x = ((Number) v).intValue();
					u8(x);
					u8(x >>> 8);
				}
				case 'i', 'u' -> u32(((Number) v).longValue());
				case 'x', 't' -> u64(((Number) v).longValue());
				case 'd' -> u64(Double.doubleToRawLongBits((Double) v));
				case 's', 'o' -> string((String) v);
				case 'g' -> signatureString((String) v);
				case 'v' -> {
					Variant var = (Variant) v;
					signatureString(var.signature());
					value(var.signature(), var.value());
				}
				case '(', '{' -> {
					align(8);
					List<String> inner = splitSignature(sig.substring(1, sig.length() - 1));
					List<Object> items = (List<Object>) v;
					for (int i = 0; i < inner.size(); i++) value(inner.get(i), items.get(i));
				}
				case 'a' -> {
					String elem = sig.substring(1);
					u32(0); // length, patched below
					int lengthAt = size - 4;
					align(alignment(elem));
					int start = size;
					if (elem.equals("y")) {
						bytes((byte[]) v);
					} else if (elem.startsWith("{")) {
						List<String> kv = splitSignature(elem.substring(1, elem.length() - 1));
						for (Map.Entry<Object, Object> e : ((Map<Object, Object>) v).entrySet()) {
							align(8);
							value(kv.get(0), e.getKey());
							value(kv.get(1), e.getValue());
						}
					} else {
						for (Object item : (List<Object>) v) value(elem, item);
					}
					putU32(lengthAt, size - start);
				}
				default -> throw new IllegalArgumentException("D-Bus type " + c);
			}
		}
	}

	// ---- reading ----

	/** Reads one whole message from the stream. */
	static Message read(InputStream in) throws IOException {
		DataInputStream data = new DataInputStream(in);
		byte[] fixed = new byte[16];
		data.readFully(fixed);
		ByteOrder order = fixed[0] == 'l' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
		ByteBuffer head = ByteBuffer.wrap(fixed).order(order);
		long bodyLength = Integer.toUnsignedLong(head.getInt(4));
		long fieldsLength = Integer.toUnsignedLong(head.getInt(12));
		long headerLength = 16 + fieldsLength;
		long padded = (headerLength + 7) & ~7L;
		if (padded + bodyLength > 64L * 1024 * 1024) throw new IOException("D-Bus message too long");
		byte[] all = new byte[(int) (padded + bodyLength)];
		System.arraycopy(fixed, 0, all, 0, 16);
		data.readFully(all, 16, all.length - 16);
		return parse(all);
	}

	/** Parses a whole message (header and body). */
	@SuppressWarnings("unchecked")
	static Message parse(byte[] all) {
		ByteOrder order = all[0] == 'l' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
		Reader r = new Reader(ByteBuffer.wrap(all).order(order));
		int type = all[1];
		r.pos = 8;
		long serial = r.u32();
		List<Object> fields = (List<Object>) r.value("a(yv)");
		r.align(8);
		long replySerial = 0;
		String path = null, iface = null, member = null, errorName = null, sender = null, signature = "";
		for (Object f : fields) {
			List<Object> field = (List<Object>) f;
			int code = ((Long) field.get(0)).intValue();
			Object v = ((Variant) field.get(1)).value();
			switch (code) {
				case F_PATH -> path = (String) v;
				case F_INTERFACE -> iface = (String) v;
				case F_MEMBER -> member = (String) v;
				case F_ERROR_NAME -> errorName = (String) v;
				case F_REPLY_SERIAL -> replySerial = (Long) v;
				case F_SENDER -> sender = (String) v;
				case F_SIGNATURE -> signature = (String) v;
				default -> { }
			}
		}
		// the body's alignment is counted from the start of the message, and it starts 8-aligned
		List<Object> body = new ArrayList<>();
		for (String t : splitSignature(signature)) body.add(r.value(t));
		return new Message(type, serial, replySerial, path, iface, member, errorName, sender, signature, body);
	}

	static final class Reader {
		private final ByteBuffer buf;
		int pos;

		Reader(ByteBuffer buf) {
			this.buf = buf;
		}

		void align(int n) {
			pos = (pos + n - 1) / n * n;
		}

		long u32() {
			align(4);
			long v = Integer.toUnsignedLong(buf.getInt(pos));
			pos += 4;
			return v;
		}

		private String string(long length) {
			String s = new String(buf.array(), pos, (int) length, StandardCharsets.UTF_8);
			pos += (int) length + 1;
			return s;
		}

		Object value(String sig) {
			char c = sig.charAt(0);
			switch (c) {
				case 'y':
					return (long) (buf.get(pos++) & 0xff);
				case 'b':
					return u32() != 0;
				case 'n': {
					align(2);
					long v = buf.getShort(pos);
					pos += 2;
					return v;
				}
				case 'q': {
					align(2);
					long v = buf.getShort(pos) & 0xffff;
					pos += 2;
					return v;
				}
				case 'i': {
					align(4);
					long v = buf.getInt(pos);
					pos += 4;
					return v;
				}
				case 'u':
					return u32();
				case 'x':
				case 't': {
					align(8);
					long v = buf.getLong(pos);
					pos += 8;
					return v;
				}
				case 'd': {
					align(8);
					double v = buf.getDouble(pos);
					pos += 8;
					return v;
				}
				case 's':
				case 'o':
					return string(u32());
				case 'g':
					return string(buf.get(pos++) & 0xff);
				case 'v': {
					String inner = (String) value("g");
					return new Variant(inner, value(inner));
				}
				case '(':
				case '{': {
					align(8);
					List<Object> items = new ArrayList<>();
					for (String t : splitSignature(sig.substring(1, sig.length() - 1))) items.add(value(t));
					return items;
				}
				case 'a': {
					String elem = sig.substring(1);
					long length = u32();
					align(alignment(elem));
					int end = pos + (int) length;
					if (elem.equals("y")) {
						byte[] b = Arrays.copyOfRange(buf.array(), pos, end);
						pos = end;
						return b;
					}
					if (elem.startsWith("{")) {
						List<String> kv = splitSignature(elem.substring(1, elem.length() - 1));
						Map<Object, Object> map = new LinkedHashMap<>();
						while (pos < end) {
							align(8);
							Object k = value(kv.get(0));
							map.put(k, value(kv.get(1)));
						}
						return map;
					}
					List<Object> items = new ArrayList<>();
					while (pos < end) items.add(value(elem));
					return items;
				}
				default:
					throw new IllegalArgumentException("D-Bus type " + c);
			}
		}
	}

	// ---- signatures ----

	/** "sa{sv}u" -> ["s", "a{sv}", "u"] */
	static List<String> splitSignature(String sig) {
		List<String> out = new ArrayList<>();
		int i = 0;
		while (i < sig.length()) {
			int end = endOfType(sig, i);
			out.add(sig.substring(i, end));
			i = end;
		}
		return out;
	}

	private static int endOfType(String sig, int i) {
		char c = sig.charAt(i);
		if (c == 'a') return endOfType(sig, i + 1);
		if (c == '(' || c == '{') {
			char close = c == '(' ? ')' : '}';
			int j = i + 1;
			while (sig.charAt(j) != close) j = endOfType(sig, j);
			return j + 1;
		}
		return i + 1;
	}

	static int alignment(String sig) {
		return switch (sig.charAt(0)) {
			case 'y', 'g', 'v' -> 1;
			case 'n', 'q' -> 2;
			case 'x', 't', 'd', '(', '{' -> 8;
			default -> 4; // b i u s o a
		};
	}
}
