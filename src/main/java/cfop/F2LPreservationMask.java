package cfop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record F2LPreservationMask(int bits) {
    private static final int ALL_BITS = (1 << F2LSlot.values().length) - 1;

    public F2LPreservationMask {
        if ((bits & ~ALL_BITS) != 0) {
            throw new IllegalArgumentException("Invalid F2L preservation mask bits: " + bits);
        }
    }

    public static F2LPreservationMask empty() {
        return new F2LPreservationMask(0);
    }

    public static F2LPreservationMask of(Collection<F2LSlot> slots) {
        if (slots == null) {
            throw new IllegalArgumentException("slots cannot be null");
        }
        int bits = 0;
        for (var slot : slots) {
            if (slot == null) {
                throw new IllegalArgumentException("slots cannot contain null");
            }
            bits |= bitFor(slot);
        }
        return new F2LPreservationMask(bits);
    }

    public boolean contains(F2LSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        return (bits & bitFor(slot)) != 0;
    }

    public boolean preservesAll(F2LPreservationMask required) {
        if (required == null) {
            throw new IllegalArgumentException("required cannot be null");
        }
        return (bits & required.bits) == required.bits;
    }

    public List<F2LSlot> slots() {
        var slots = new ArrayList<F2LSlot>();
        for (var slot : F2LSlot.values()) {
            if (contains(slot)) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    @Override
    public String toString() {
        return slots().toString();
    }

    private static int bitFor(F2LSlot slot) {
        return 1 << slot.ordinal();
    }
}
