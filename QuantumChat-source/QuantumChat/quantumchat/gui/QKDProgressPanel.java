package quantumchat.gui;

import quantumchat.core.AliceProtocol;
import quantumchat.core.BobProtocol;
import quantumchat.core.QKDChannel;
import quantumchat.core.QKDResult;
import quantumchat.net.MessageTransport;
import quantumchat.net.SocketMessageTransport;
import quantumchat.net.TransportQKDChannel;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.net.ServerSocket;
import java.net.Socket;

/** Connects over the network (as host or joiner), runs the BB84 exchange and shows it live. */
public class QKDProgressPanel extends JPanel implements Disposable, Theme.Themed {

    public interface KeyExchangeListener {
        void onSuccess(MessageTransport transport, byte[] aesKey, boolean isHost);
    }

    private final SetupPanel.Params params;
    private final KeyExchangeListener onSuccess;
    private final Runnable onBack;

    private final ProtocolDashboard dashboard = new ProtocolDashboard();
    private final Widgets.SecondaryButton cancelButton = new Widgets.SecondaryButton("Cancel");

    private volatile ServerSocket serverSocket;
    private volatile Socket socket;
    private volatile MessageTransport transport;
    private volatile boolean handedOver = false;
    private SwingWorker<QKDResult, Void> worker;

    public QKDProgressPanel(SetupPanel.Params params, KeyExchangeListener onSuccess, Runnable onBack) {
        this.params = params;
        this.onSuccess = onSuccess;
        this.onBack = onBack;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        add(dashboard, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        cancelButton.addActionListener(e -> cancel());
        bottom.add(cancelButton);
        add(bottom, BorderLayout.SOUTH);

        dashboard.setConfig(params.config);
        applyTheme();
    }

    @Override
    public void applyTheme() {
        setOpaque(true);
        setBackground(Theme.p().bg);
    }

    public void start() {
        final boolean isHost = params.mode == SetupPanel.Mode.HOST;
        dashboard.reset();
        dashboard.setConfig(params.config);

        worker = new SwingWorker<QKDResult, Void>() {
            @Override
            protected QKDResult doInBackground() throws Exception {
                if (isHost) {
                    say("Waiting for a peer to connect on port " + params.port + "...");
                    serverSocket = new ServerSocket(params.port);
                    socket = serverSocket.accept();
                } else {
                    say("Connecting to " + params.host + ":" + params.port + "...");
                    socket = new Socket(params.host, params.port);
                }
                say("Connected. Starting the BB84 key exchange.");
                transport = new SocketMessageTransport(socket);
                QKDChannel channel = new TransportQKDChannel(transport);
                SwingUtilities.invokeLater(() -> dashboard.appendLog("Config: " + params.config));

                return isHost
                        ? new AliceProtocol().run(channel, params.config, dashboard.forParty(null, true))
                        : new BobProtocol().run(channel, params.config, dashboard.forParty(null, true));
            }

            @Override
            protected void done() {
                QuantumChatGUI.status("Ready");
                try {
                    QKDResult result = get();
                    if (result.success) {
                        handedOver = true;
                        onSuccess.onSuccess(transport, result.aesKey, isHost);
                    } else {
                        dashboard.showOutcome(false, "Key exchange failed: " + result.abortReason);
                        becomeBackButton();
                    }
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    dashboard.showOutcome(false, "Connection error: " + c.getMessage());
                    becomeBackButton();
                }
            }
        };
        QuantumChatGUI.status("Key exchange in progress...");
        worker.execute();
    }

    private void say(String text) {
        SwingUtilities.invokeLater(() -> {
            dashboard.setHeadline(text, ProtocolDashboard.Tone.NONE);
            dashboard.appendLog(text);
        });
    }

    private void becomeBackButton() {
        cancelButton.setText("Back to setup");
        for (java.awt.event.ActionListener al : cancelButton.getActionListeners()) {
            cancelButton.removeActionListener(al);
        }
        cancelButton.addActionListener(e -> onBack.run());
    }

    private void cancel() {
        dispose();
        onBack.run();
    }

    @Override
    public void dispose() {
        if (handedOver) {
            return; // the chat screen owns the connection now
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
            // best-effort cleanup
        }
        if (worker != null) {
            worker.cancel(true);
        }
    }
}
