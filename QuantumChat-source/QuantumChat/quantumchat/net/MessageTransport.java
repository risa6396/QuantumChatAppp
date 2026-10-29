package quantumchat.net;

/**
 * A minimal, pluggable byte-message transport: send one frame, receive one
 * frame (blocking). Both the QKD handshake and the subsequent encrypted
 * chat traffic ride on top of this, so swapping the transport (real socket
 * vs. in-memory loopback) changes nothing else in the application.
 */
public interface MessageTransport {

    void send(byte[] data) throws Exception;

    byte[] receive() throws Exception;

    void close() throws Exception;
}
