package jusePack.comm;

/**
 * A message between robots.
 *
 * The payload is JSON-like data: {@code String}, {@code Double} (all numbers), {@code Boolean},
 * {@code null}, {@code Map<String,Object>} and {@code List<Object>}. It is copied when sent,
 * so the receiver gets its own copy, and the same message can travel to remote (UDP) clients.
 *
 * @param from    id of the sender
 * @param to      id of the receiver, or {@link #BROADCAST}
 * @param sentAt  simulated time when it was sent (s)
 * @param payload the content
 */
public record Message(int from, int to, double sentAt, Object payload) {

    /** Receiver id of a broadcast message. */
    public static final int BROADCAST = -1;

    public boolean isBroadcast() { return to == BROADCAST; }

}
