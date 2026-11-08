package mcjty.meecreeps.teleport;

public final class BlenderDeathState {

    private static final long MARKER_LIFETIME_NANOS = 10_000_000_000L;
    private static volatile long pendingUntilNanos;

    private BlenderDeathState() {
    }

    public static void markPending() {
        pendingUntilNanos = System.nanoTime() + MARKER_LIFETIME_NANOS;
    }

    public static boolean consumePending() {
        long expiry = pendingUntilNanos;
        pendingUntilNanos = 0L;
        return expiry != 0L && System.nanoTime() <= expiry;
    }
}
