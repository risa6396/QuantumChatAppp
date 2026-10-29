package quantumchat.gui;

/** Implemented by session screens that own connections/threads which must be released when the screen is closed. */
public interface Disposable {
    void dispose();
}
