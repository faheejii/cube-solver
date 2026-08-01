package solver;

import java.util.Objects;

public final class F2LDatabaseMissException extends IllegalStateException {
    private final F2LDatabaseMissContext context;

    public F2LDatabaseMissException(F2LDatabaseMissContext context) {
        super(messageFor(context));
        this.context = Objects.requireNonNull(context, "context");
    }

    public F2LDatabaseMissContext context() {
        return context;
    }

    private static String messageFor(F2LDatabaseMissContext context) {
        Objects.requireNonNull(context, "context");
        return "F2L " + context.phase().name().toLowerCase()
                + " database miss for slot " + context.insertSlot()
                + ", preservation=" + context.requiredPreservation()
                + ", signature=" + context.signature();
    }
}
