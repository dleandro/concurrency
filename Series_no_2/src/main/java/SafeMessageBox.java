import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SafeMessageBox<M> {

    private class MsgHolder {
        private final M msg;
        private AtomicInteger lives;

        MsgHolder(M msg, int lvs) {
            this.msg = msg;
            this.lives = new AtomicInteger(lvs);
        }
    }

    private AtomicReference<MsgHolder> msgHolder = null;

    // simply publishes new message to the MsgHolder
    // doesnt care if all lives from previous holder have been used or not
    public void publish(M m, int lvs) {
        msgHolder = new AtomicReference<>(new MsgHolder(m, lvs));
    }

    // consume a message if MsgHolder isn't null and its lives are bigger than 0
    public M tryConsume() {
        MsgHolder newHolder;
        AtomicReference<MsgHolder> observedHolder;
        int observedLives;
        M observedMessage;

        while (true) {

            // observe holder so that we can extract its properties later making sure that we don't change
            // properties of an already changed msgHolder
            observedHolder = msgHolder;

            // check if there is any msg to be consumed
            if (observedHolder == null) {
                return null;
            }

            // observe number of lives and message to be consumed
            observedLives = observedHolder.get().lives.get();
            observedMessage = observedHolder.get().msg;

            // check if number of lives is 1, if so we can´t consume message
            // and we should change msgHolder value to assure that the next thread exits
            // as early as possible without consuming message
            if (observedLives == 0) {
                msgHolder = null;
                return null;
            }

            newHolder = new MsgHolder(observedMessage, observedLives - 1);

            // decrement lives and consume message only if msgHolder is still equal to the observed one
            if (msgHolder.compareAndSet(observedHolder.get(), newHolder)) {
                return observedMessage;
            }
        }
    }
}
