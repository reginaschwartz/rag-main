import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * "transient" marks a field that Java serialization skips: it is never written to the byte stream, and
 * on the way back it is left at its default value (null, 0, false) because deserialization does not run
 * constructors or field initializers.
 */
class TransientDemo {

    static class Session implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String user;        // written to the stream
        private final int loginCount;     // written to the stream

        private transient String password;    // a secret, deliberately not written
        private transient String displayName; // derived data, cheaper to rebuild than to transport

        Session(String user, String password, int loginCount) {
            this.user = user;
            this.password = password;
            this.loginCount = loginCount;
            this.displayName = buildDisplayName();
        }

        private String buildDisplayName() {
            return user.toUpperCase() + " (" + loginCount + " logins)";
        }

        /** Called by the JVM after the non-transient fields are restored. */
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            this.displayName = buildDisplayName();
        }

        @Override
        public String toString() {
            return "Session[user=" + user + ", loginCount=" + loginCount
                    + ", password=" + password + ", displayName=" + displayName + "]";
        }
    }

    /** A Thread cannot be serialized, and without transient it breaks the whole object. */
    static class WithoutTransient implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Thread worker = new Thread(() -> { });
    }

    static class WithTransient implements Serializable {
        private static final long serialVersionUID = 1L;
        private final transient Thread worker = new Thread(() -> { });
    }

    public static void main(String[] args) throws Exception {
        Session before = new Session("rinat", "s3cret", 42);
        byte[] bytes = serialize(before);
        Session after = (Session) deserialize(bytes);

        System.out.println("before write            : " + before);
        System.out.println("after read              : " + after);
        System.out.println("stream contains 'rinat' : " + streamContains(bytes, "rinat"));
        System.out.println("stream contains 's3cret': " + streamContains(bytes, "s3cret"));

        try {
            serialize(new WithoutTransient());
            System.out.println("without transient       : serialized (unexpected)");
        } catch (NotSerializableException exception) {
            System.out.println("without transient       : NotSerializableException on "
                    + exception.getMessage());
        }

        WithTransient restored = (WithTransient) deserialize(serialize(new WithTransient()));
        System.out.println("with transient          : serialized fine, worker after read = "
                + restored.worker);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(value);
        }
        return buffer.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    /** Strings land in the stream as readable bytes, so this shows what was actually written. */
    private static boolean streamContains(byte[] bytes, String text) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(text);
    }
}
