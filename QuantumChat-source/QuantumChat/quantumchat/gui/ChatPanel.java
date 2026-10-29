package quantumchat.gui;

import quantumchat.chat.ChatConnection;
import quantumchat.chat.FileInfo;
import quantumchat.chat.IncomingFile;
import quantumchat.chat.SecureFrame;
import quantumchat.crypto.CryptoUtil;
import quantumchat.net.MessageTransport;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An encrypted chat pane with file transfer.
 *
 * Everything you RECEIVE (text and files) arrives sealed and stays sealed
 * until you enter the session key: locked messages show only their ciphertext,
 * locked files show only "encrypted file, N chunks". A wrong key reveals
 * nothing (AES-GCM authentication fails). With the right key, text decrypts in
 * place and files can be decrypted and saved, verified against the SHA-256 the
 * sender recorded. Everything you SEND is encrypted with the session key.
 */
public class ChatPanel extends JPanel implements Theme.Themed, Disposable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageTransport transport;
    private final byte[] aesKey;
    private final String roleLabel;
    private final Runnable onDisconnect;
    private ChatConnection connection;

    private byte[] unlockedKey = null;
    private final List<Item> items = new ArrayList<>();
    private final Map<Integer, InFile> inFiles = new HashMap<>();

    private final ChatView view = new ChatView();
    private final JScrollPane scroll = new JScrollPane(view);
    private final Widgets.PromptTextField input = new Widgets.PromptTextField("Type a message...");
    private final Widgets.SecondaryButton attachButton = new Widgets.SecondaryButton("");
    private final Widgets.PrimaryButton sendButton = new Widgets.PrimaryButton("Send");
    private final Widgets.PromptTextField keyField = new Widgets.PromptTextField("Session key (64 hex characters)");
    private final Widgets.SecondaryButton unlockButton = new Widgets.SecondaryButton("Unlock");
    private final Widgets.SecondaryButton myKeyButton = new Widgets.SecondaryButton("My key...");
    private final JLabel keyStatus = new JLabel(" ");
    private final Widgets.Chip chip = new Widgets.Chip();
    private final JLabel keyIcon = new JLabel();
    private Widgets.RoundedPanel header;
    private Widgets.RoundedPanel keyBar;
    private JPanel composer;
    private String fingerprint = "?";

    public ChatPanel(MessageTransport transport, byte[] aesKey, String roleLabel, Runnable onDisconnect) {
        this.transport = transport;
        this.aesKey = aesKey;
        this.roleLabel = roleLabel;
        this.onDisconnect = onDisconnect;
        try {
            fingerprint = CryptoUtil.fingerprint(aesKey);
        } catch (Exception ignored) {
            // keep "?"
        }
        buildUI();
        installDropTarget();
        applyTheme();
    }

    // ================================================================= UI

    private void buildUI() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ---- header: avatar, title, status chip, disconnect ----
        header = new Widgets.RoundedPanel(new BorderLayout(10, 0));
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        header.add(new Widgets.Avatar(roleLabel.substring(0, 1).toUpperCase(), 38), BorderLayout.WEST);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(roleLabel);
        title.setFont(Theme.font(Font.BOLD, 14f));
        JLabel subtitle = new JLabel("AES-256-GCM  \u2022  key " + fingerprint);
        subtitle.setFont(Theme.font(Font.PLAIN, 11.5f));
        subtitle.setName("muted");
        titles.add(Box.createVerticalGlue());
        titles.add(title);
        titles.add(subtitle);
        titles.add(Box.createVerticalGlue());
        header.add(titles, BorderLayout.CENTER);

        Widgets.SecondaryButton disconnect = new Widgets.SecondaryButton("Disconnect");
        disconnect.addActionListener(e -> doDisconnect());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(chip);
        right.add(disconnect);
        header.add(right, BorderLayout.EAST);

        // ---- key bar ----
        keyBar = new Widgets.RoundedPanel(new BorderLayout(8, 4)).fill(Widgets.RoundedPanel.Fill.ALT);
        keyBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        keyField.setFont(Theme.mono(11.5f));
        keyField.addActionListener(e -> toggleLock());
        unlockButton.addActionListener(e -> toggleLock());
        myKeyButton.addActionListener(e -> showMyKey());
        JPanel keyButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        keyButtons.setOpaque(false);
        keyButtons.add(unlockButton);
        keyButtons.add(myKeyButton);
        keyBar.add(keyIcon, BorderLayout.WEST);
        keyBar.add(keyField, BorderLayout.CENTER);
        keyBar.add(keyButtons, BorderLayout.EAST);
        keyStatus.setFont(Theme.font(Font.PLAIN, 11.5f));
        keyBar.add(keyStatus, BorderLayout.SOUTH);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        header.setAlignmentX(LEFT_ALIGNMENT);
        keyBar.setAlignmentX(LEFT_ALIGNMENT);
        top.add(header);
        top.add(Box.createVerticalStrut(8));
        top.add(keyBar);
        add(top, BorderLayout.NORTH);

        // ---- conversation ----
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // ---- composer ----
        composer = new JPanel(new BorderLayout(8, 0));
        composer.setOpaque(false);
        attachButton.setToolTipText("Attach and send a file (or drop files onto the conversation)");
        attachButton.addActionListener(e -> chooseAndSendFile());
        input.addActionListener(e -> sendCurrentMessage());
        sendButton.addActionListener(e -> sendCurrentMessage());
        composer.add(attachButton, BorderLayout.WEST);
        composer.add(input, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);
        add(composer, BorderLayout.SOUTH);

        setKeyStatus("Locked: what you receive stays encrypted until you enter the key.", null);
        updateChip();
    }

    private void installDropTarget() {
        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        sendFile(f);
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        setTransferHandler(handler);
        view.setTransferHandler(handler);
        scroll.setTransferHandler(handler);
        scroll.getViewport().setTransferHandler(handler);
    }

    @Override
    public void applyTheme() {
        Theme.Palette p = Theme.p();
        setOpaque(true);
        setBackground(p.bg);
        scroll.setBorder(BorderFactory.createLineBorder(p.border));
        scroll.getViewport().setBackground(p.surface);
        attachButton.setIcon(Icons.of(Icons.Kind.CLIP, 18, p.text));
        sendButton.setIcon(Icons.of(Icons.Kind.SEND, 15, Color.WHITE));
        for (java.awt.Component c : header.getComponents()) {
            recolorMuted(c, p);
        }
        updateChip();
        for (Item it : items) {
            it.refresh();
        }
        repaint();
    }

    private void recolorMuted(java.awt.Component c, Theme.Palette p) {
        if (c instanceof JLabel && "muted".equals(c.getName())) {
            c.setForeground(p.muted);
        }
        if (c instanceof java.awt.Container) {
            for (java.awt.Component ch : ((java.awt.Container) c).getComponents()) {
                recolorMuted(ch, p);
            }
        }
    }

    private void updateChip() {
        Theme.Palette p = Theme.p();
        if (unlockedKey == null) {
            chip.setText("Locked");
            chip.setIcon(Icons.of(Icons.Kind.LOCK, 13, p.warn));
            chip.setTone(Widgets.mix(p.surface, p.warn, 0.22f), p.warn);
            keyIcon.setIcon(Icons.of(Icons.Kind.LOCK, 18, p.warn));
        } else {
            chip.setText("Unlocked");
            chip.setIcon(Icons.of(Icons.Kind.UNLOCK, 13, p.ok));
            chip.setTone(Widgets.mix(p.surface, p.ok, 0.20f), p.ok);
            keyIcon.setIcon(Icons.of(Icons.Kind.UNLOCK, 18, p.ok));
        }
    }

    private void setKeyStatus(String text, Color color) {
        keyStatus.setText(text);
        keyStatus.setForeground(color != null ? color : Theme.p().muted);
    }

    // ================================================================= lifecycle

    public void start() {
        connection = new ChatConnection(transport, aesKey);
        connection.startListening(new ChatConnection.Listener() {
            @Override
            public void onFrame(SecureFrame frame) {
                SwingUtilities.invokeLater(() -> handleFrame(frame));
            }

            @Override
            public void onDisconnected() {
                SwingUtilities.invokeLater(() -> {
                    addSystem("Peer disconnected.");
                    setComposerEnabled(false);
                });
            }

            @Override
            public void onInvalidFrame(String reason) {
                SwingUtilities.invokeLater(() -> addSystem("Dropped a malformed message (" + reason + ")."));
            }
        });
        addSystem("Secure channel established. Everything you receive stays encrypted until you enter the session key.");
    }

    private void setComposerEnabled(boolean on) {
        input.setEnabled(on);
        sendButton.setEnabled(on);
        attachButton.setEnabled(on);
    }

    private void doDisconnect() {
        dispose();
        setComposerEnabled(false);
        onDisconnect.run();
    }

    @Override
    public void dispose() {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isUnlocked() {
        return unlockedKey != null;
    }

    // ================================================================= key handling

    private void toggleLock() {
        if (unlockedKey != null) {
            unlockedKey = null;
            unlockButton.setText("Unlock");
            keyField.setEnabled(true);
            setKeyStatus("Locked again: received messages and files are encrypted.", null);
            refreshAll();
        } else {
            unlockWithKeyHex(keyField.getText());
        }
    }

    /**
     * Tries to unlock with a key given as hex. Succeeds only if it is exactly
     * the session key; a wrong key can't decrypt anything (GCM authentication
     * fails), so nothing is revealed.
     */
    public boolean unlockWithKeyHex(String hex) {
        Theme.Palette p = Theme.p();
        byte[] entered;
        try {
            entered = CryptoUtil.fromHex(hex);
        } catch (IllegalArgumentException ex) {
            setKeyStatus("That is not valid hex. The key is 64 characters (0-9, a-f).", p.err);
            return false;
        }
        if (entered.length != 32) {
            setKeyStatus("Wrong length: the key must be exactly 64 hex characters (256 bits).", p.err);
            return false;
        }
        if (!MessageDigest.isEqual(entered, aesKey)) {
            setKeyStatus("Incorrect key. Decryption failed; everything stays encrypted.", p.err);
            keyField.selectAll();
            return false;
        }
        unlockedKey = entered;
        keyField.setText("");
        keyField.setEnabled(false);
        unlockButton.setText("Lock");
        setKeyStatus("Unlocked: received messages are decrypted, files can be saved.", p.ok);
        refreshAll();
        return true;
    }

    private void refreshAll() {
        updateChip();
        for (Item it : items) {
            it.refresh();
        }
        view.revalidate();
        view.repaint();
        view.scrollToBottomLater();
    }

    private void showMyKey() {
        String hex = CryptoUtil.toHex(aesKey);
        JTextArea area = new JTextArea(hex);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setFont(Theme.mono(12f));
        area.setColumns(34);
        area.setRows(3);
        JPanel panel = new JPanel(new BorderLayout(6, 8));
        panel.add(new JLabel("<html>Your 256-bit session key (derived via BB84).<br>"
                + "Anyone holding it can decrypt this conversation.</html>"), BorderLayout.NORTH);
        panel.add(area, BorderLayout.CENTER);
        int choice = JOptionPane.showOptionDialog(this, panel, "Session key",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[] {"Copy to clipboard", "Close"}, "Close");
        if (choice == 0) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(hex), null);
        }
    }

    // ================================================================= conversation items

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    private void addItem(Item it) {
        items.add(it);
        view.addBubble(it.bubble);
    }

    private void addSystem(String text) {
        Bubble b = new Bubble(Bubble.Side.SYSTEM);
        b.setBody(text);
        view.addBubble(b);
    }

    private abstract class Item {
        final Bubble bubble;

        Item(Bubble bubble) {
            this.bubble = bubble;
        }

        abstract void refresh();
    }

    /** A message I sent. */
    private final class OutText extends Item {
        OutText(String text) {
            super(new Bubble(Bubble.Side.MINE));
            bubble.setMeta("You  \u2022  " + now());
            bubble.setBody(text);
        }

        @Override void refresh() { }
    }

    /** A text message received from the peer. */
    private final class InText extends Item {
        private final SecureFrame frame;
        private final String time = now();

        InText(SecureFrame frame) {
            super(new Bubble(Bubble.Side.THEIRS));
            this.frame = frame;
            refresh();
        }

        @Override
        void refresh() {
            if (unlockedKey == null) {
                String b64 = CryptoUtil.toBase64(frame.sealed);
                bubble.setStyle(Bubble.Style.ENCRYPTED);
                bubble.setMeta("Peer  \u2022  " + time + "  \u2022  encrypted");
                bubble.setBody("Encrypted message (" + frame.sealed.length + " bytes)\n"
                        + Widgets.ellipsizeEnd(b64, 150));
                bubble.setCopyText(b64);
                return;
            }
            byte[] plain = frame.tryOpen(unlockedKey);
            bubble.setCopyText(null);
            if (plain != null) {
                bubble.setStyle(Bubble.Style.NORMAL);
                bubble.setMeta("Peer  \u2022  " + time);
                bubble.setBody(new String(plain, StandardCharsets.UTF_8));
            } else {
                bubble.setStyle(Bubble.Style.ERROR);
                bubble.setMeta("Peer  \u2022  " + time);
                bubble.setBody("Could not decrypt this message (wrong key or it was tampered with).");
            }
        }
    }

    /** A file I am sending. */
    private final class OutFile extends Item {
        final FileCard card = new FileCard();
        private final File file;

        OutFile(File file) {
            super(new Bubble(Bubble.Side.MINE));
            this.file = file;
            bubble.setMeta("You  \u2022  " + now());
            bubble.setAttachment(card);
            card.setKind(Icons.Kind.FILE);
            card.setFileName(file.getName());
            card.setDetail(Widgets.humanSize(file.length()) + "  \u2022  encrypting and sending...");
            card.setProgress(0);
        }

        void done() {
            card.setDetail(Widgets.humanSize(file.length()) + "  \u2022  sent, end-to-end encrypted");
            card.setProgress(-1);
            bubble.revalidate();
        }

        void failed(String why) {
            bubble.setStyle(Bubble.Style.ERROR);
            card.setDetail("Not sent: " + why);
            card.setProgress(-1);
            bubble.revalidate();
        }

        @Override void refresh() { }
    }

    /** A file received from the peer: collected while sealed, revealed only with the key. */
    private final class InFile extends Item {
        final IncomingFile inc;
        final FileCard card = new FileCard();
        private final String time = now();
        File savedTo;
        boolean saving;
        String saveError;
        private int lastPct = -1;

        InFile(IncomingFile inc) {
            super(new Bubble(Bubble.Side.THEIRS));
            this.inc = inc;
            bubble.setAttachment(card);
            refresh();
        }

        /** Cheap per-chunk update: only the progress bar and counter, never a full re-layout. */
        void onChunk() {
            if (inc.isComplete()) {
                refresh();
                return;
            }
            int exp = inc.expectedChunks();
            int pct = exp > 0 ? inc.receivedChunks() * 100 / exp : 0;
            if (pct != lastPct) {
                lastPct = pct;
                card.setProgress(pct);
                card.setDetail(receivingText());
            }
        }

        private String receivingText() {
            int exp = inc.expectedChunks();
            return "Receiving...  " + inc.receivedChunks() + " / " + (exp < 0 ? "?" : String.valueOf(exp)) + " chunks";
        }

        @Override
        void refresh() {
            boolean complete = inc.isComplete();
            int chunks = Math.max(0, inc.expectedChunks());
            if (unlockedKey == null) {
                bubble.setStyle(Bubble.Style.ENCRYPTED);
                bubble.setMeta("Peer  \u2022  " + time + "  \u2022  encrypted file");
                card.setKind(Icons.Kind.LOCK);
                card.setFileName("Encrypted file");
                if (complete) {
                    card.setDetail(chunks + " chunks, " + Widgets.humanSize(inc.encryptedBytes())
                            + " sealed. Enter the key to open it.");
                    card.setProgress(-1);
                } else {
                    card.setDetail(receivingText());
                    int exp = inc.expectedChunks();
                    card.setProgress(exp > 0 ? inc.receivedChunks() * 100 / exp : 0);
                }
                card.setAction(null, null);
                return;
            }

            FileInfo info = inc.readInfo(unlockedKey);
            card.setKind(Icons.Kind.FILE);
            if (info == null) {
                bubble.setStyle(inc.hasMeta() ? Bubble.Style.ERROR : Bubble.Style.ENCRYPTED);
                bubble.setMeta("Peer  \u2022  " + time);
                card.setFileName(inc.hasMeta() ? "Unreadable file" : "Incoming file");
                card.setDetail(inc.hasMeta() ? "Could not decrypt the file header (tampered?)." : "Waiting for the file header...");
                card.setProgress(-1);
                card.setAction(null, null);
                return;
            }

            bubble.setStyle(Bubble.Style.NORMAL);
            bubble.setMeta("Peer  \u2022  " + time);
            card.setFileName(info.name);
            if (saving) {
                card.setDetail(Widgets.humanSize(info.size) + "  \u2022  decrypting and saving...");
                card.setAction(null, null);
            } else if (saveError != null) {
                bubble.setStyle(Bubble.Style.ERROR);
                card.setDetail("Save failed: " + saveError);
                card.setProgress(-1);
                card.setAction("Retry...", () -> promptSave(this));
            } else if (savedTo != null) {
                card.setDetail("Saved  \u2022  SHA-256 verified");
                card.setToolTipText("Saved to " + savedTo.getAbsolutePath());
                card.setProgress(-1);
                card.setAction("Save again...", () -> promptSave(this));
            } else if (!complete) {
                card.setDetail(Widgets.humanSize(info.size) + "  \u2022  " + receivingText());
                int exp = inc.expectedChunks();
                card.setProgress(exp > 0 ? inc.receivedChunks() * 100 / exp : 0);
                card.setAction(null, null);
            } else {
                card.setDetail(Widgets.humanSize(info.size) + "  \u2022  decrypted, not saved yet");
                card.setProgress(-1);
                card.setAction("Save...", () -> promptSave(this));
            }
            bubble.revalidate();
        }
    }

    // ================================================================= incoming frames

    private void handleFrame(SecureFrame f) {
        switch (f.type) {
            case SecureFrame.TEXT:
                addItem(new InText(f));
                break;
            case SecureFrame.FILE_META:
            case SecureFrame.FILE_CHUNK: {
                InFile existing = inFiles.get(f.transferId);
                if (existing == null) {
                    IncomingFile inc = new IncomingFile(f.transferId);
                    if (!inc.accept(f)) {
                        addSystem("Ignored an invalid file transfer.");
                        return;
                    }
                    InFile created = new InFile(inc);
                    inFiles.put(f.transferId, created);
                    addItem(created);
                } else if (existing.inc.accept(f)) {
                    if (f.type == SecureFrame.FILE_META) {
                        existing.refresh();
                    } else {
                        existing.onChunk();
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    // ================================================================= sending

    private void sendCurrentMessage() {
        String text = input.getText().trim();
        if (text.isEmpty() || connection == null) {
            return;
        }
        sendText(text);
        input.setText("");
    }

    public void sendText(String text) {
        try {
            connection.sendText(text);
            addItem(new OutText(text));
        } catch (Exception ex) {
            addSystem("Could not send: " + ex.getMessage());
        }
    }

    private void chooseAndSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file to send (encrypted)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            sendFile(chooser.getSelectedFile());
        }
    }

    /** Encrypts and sends a file in the background, showing a progress bar in the conversation. */
    public void sendFile(File file) {
        if (connection == null || !input.isEnabled()) {
            return;
        }
        if (!file.isFile()) {
            addSystem("\"" + file.getName() + "\" is not a regular file, so it was not sent.");
            return;
        }
        if (file.length() > ChatConnection.MAX_FILE_BYTES) {
            addSystem("\"" + file.getName() + "\" is larger than the "
                    + (ChatConnection.MAX_FILE_BYTES >> 20) + " MB limit, so it was not sent.");
            return;
        }
        OutFile item = new OutFile(file);
        addItem(item);
        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                connection.sendFile(file, (done, total) -> publish(total > 0 ? (int) (done * 100 / total) : 100));
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                item.card.setProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                try {
                    get();
                    item.done();
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    item.failed(String.valueOf(c.getMessage()));
                }
            }
        }.execute();
    }

    // ================================================================= saving received files

    private void promptSave(InFile it) {
        if (unlockedKey == null) {
            return;
        }
        FileInfo info = it.inc.readInfo(unlockedKey);
        if (info == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save decrypted file");
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), Widgets.safeFileName(info.name)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = chooser.getSelectedFile();
        if (dest.exists() && JOptionPane.showConfirmDialog(this,
                "\"" + dest.getName() + "\" already exists. Replace it?", "Replace file?",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        saveTo(it, dest);
    }

    /** Saves the most recently received file (if unlocked and complete); used by automated tests. */
    public void saveLastReceivedFile(File dest) {
        InFile last = null;
        for (Item it : items) {
            if (it instanceof InFile) {
                last = (InFile) it;
            }
        }
        if (last != null && unlockedKey != null) {
            saveTo(last, dest);
        }
    }

    private void saveTo(InFile it, File dest) {
        final byte[] key = unlockedKey;
        if (key == null || it.saving) {
            return;
        }
        it.saving = true;
        it.saveError = null;
        it.card.setProgress(0);
        it.refresh();
        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                it.inc.decryptTo(dest, key, (done, total) -> publish(total > 0 ? (int) (done * 100 / total) : 100));
                return null;
            }

            @Override
            protected void process(List<Integer> v) {
                it.card.setProgress(v.get(v.size() - 1));
            }

            @Override
            protected void done() {
                it.saving = false;
                try {
                    get();
                    it.savedTo = dest;
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    it.saveError = String.valueOf(c.getMessage());
                }
                it.refresh();
            }
        }.execute();
    }
}
