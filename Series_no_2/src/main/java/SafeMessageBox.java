import java.util.concurrent.atomic.AtomicInteger;

public class SafeMessageBox<M> {

    private class MsgHolder {
        private final M msg;
        private AtomicInteger lives;

        MsgHolder(M msg, int lvs) {
            this.msg = msg;
            this.lives = new AtomicInteger(lvs);
        }
    }

    private MsgHolder msgHolder = null;

    // simply publishes new message to the MsgHolder
    // doesnt care if all lives have been used or not
    public void publish(M m, int lvs) {
        msgHolder = new MsgHolder(m, lvs);
    }

    // consume the message if MsgHolder has one and lives are bigger than 0
    // all done in a thread-safe way
    public M tryConsume() {
        MsgHolder observedHolder;
        int observedLives;
        M observedMessage;

        //CAS
        do {
            // observe holder so that we can extract its properties later
            observedHolder = msgHolder;

            // check if there is any msg to be consumed
            if (observedHolder == null) {
                return null;
            }

            // observe number of lives and message to be consumed
            observedLives = observedHolder.lives.get();
            observedMessage = observedHolder.msg;

            // check if number of lives is 0, if so we can´t consume message
            // and we should change msgHolder value to assure that the next thread exits
            // as early as possible without consuming message
            if (observedLives == 0) {
                msgHolder = null;
                return null;
            }

            if (observedLives > 0) {
                return observedMessage;
            }

        } while (!msgHolder.lives.compareAndSet(observedLives, observedLives - 1));

        return null;
    }

}
