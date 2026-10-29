import quantumchat.chat.*;
import quantumchat.crypto.CryptoUtil;
import quantumchat.net.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class FileTransferTest {
    static int failures = 0;
    static final SecureRandom RNG = new SecureRandom();

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failures++;
    }

    /** Collects frames delivered by a ChatConnection listener. */
    static class Collector implements ChatConnection.Listener {
        final BlockingQueue<SecureFrame> frames = new LinkedBlockingQueue<>();
        volatile boolean disconnected;
        public void onFrame(SecureFrame f) { frames.add(f); }
        public void onDisconnected() { disconnected = true; }
    }

    static File randomFile(int size) throws IOException {
        File f = File.createTempFile("qc_test_", ".bin");
        f.deleteOnExit();
        byte[] b = new byte[size];
        RNG.nextBytes(b);
        Files.write(f.toPath(), b);
        return f;
    }

    static List<SecureFrame> receive(Collector c, int count) throws Exception {
        List<SecureFrame> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SecureFrame f = c.frames.poll(10, TimeUnit.SECONDS);
            if (f == null) throw new TimeoutException("only got " + i + " of " + count + " frames");
            out.add(f);
        }
        return out;
    }

    static IncomingFile assemble(List<SecureFrame> frames) {
        IncomingFile inc = new IncomingFile(frames.get(0).transferId);
        for (SecureFrame f : frames) inc.accept(f);
        return inc;
    }

    public static void main(String[] args) throws Exception {
        byte[] key = new byte[32]; RNG.nextBytes(key);
        byte[] wrong = key.clone(); wrong[5] ^= 0x10;

        InMemoryMessageTransport[] pair = InMemoryMessageTransport.createPair();
        ChatConnection alice = new ChatConnection(pair[0], key);
        ChatConnection bob = new ChatConnection(pair[1], key);
        Collector atBob = new Collector();
        bob.startListening(atBob);

        System.out.println("== 1. Round-trip at many sizes (chunk size = " + ChatConnection.CHUNK_SIZE + ") ==");
        int[] sizes = {0, 1, 100, 32767, 32768, 32769, 65536, 100_000, 3_000_000};
        for (int size : sizes) {
            File src = randomFile(size);
            int chunks = (size + ChatConnection.CHUNK_SIZE - 1) / ChatConnection.CHUNK_SIZE;
            long[] lastProgress = {-1};
            alice.sendFile(src, (d, t) -> lastProgress[0] = d);
            IncomingFile inc = assemble(receive(atBob, chunks + 1));
            File dst = File.createTempFile("qc_out_", ".bin"); dst.deleteOnExit();
            boolean complete = inc.isComplete();
            FileInfo info = inc.readInfo(key);
            inc.decryptTo(dst, key, null);
            boolean same = Arrays.equals(Files.readAllBytes(src.toPath()), Files.readAllBytes(dst.toPath()));
            check(String.format("size %,9d: complete=%b chunks=%d name+size ok=%b bytes identical=%b",
                    size, complete, chunks, info != null && info.size == size && info.name.equals(src.getName()), same),
                    complete && same && info != null && info.size == size && info.name.equals(src.getName())
                            && (size == 0 || lastProgress[0] == size));
        }

        System.out.println("== 2. Wrong key reveals nothing ==");
        File f2 = randomFile(70_000);
        alice.sendFile(f2, null);
        List<SecureFrame> frames2 = receive(atBob, 4);
        IncomingFile inc2 = assemble(frames2);
        check("readInfo(wrong key) == null", inc2.readInfo(wrong) == null);
        File dst2 = File.createTempFile("qc_wrong_", ".bin"); dst2.delete();   // must not pre-exist
        boolean threw = false;
        try { inc2.decryptTo(dst2, wrong, null); } catch (Exception e) { threw = true; }
        check("decryptTo(wrong key) throws and leaves no output (" + (dst2.exists() ? "file exists!" : "no file") + ")", threw && !dst2.exists());
        check("right key still works afterwards", inc2.readInfo(key) != null);

        System.out.println("== 3. Tampering is detected ==");
        // 3a: flip one bit inside a chunk's ciphertext
        List<SecureFrame> t = new ArrayList<>(frames2);
        SecureFrame victim = t.get(2);
        byte[] bad = victim.sealed.clone(); bad[20] ^= 1;
        t.set(2, new SecureFrame(victim.type, victim.transferId, victim.index, victim.total, bad));
        IncomingFile incA = assemble(t);
        File dA = File.createTempFile("qc_t_", ".bin");
        boolean threwA = false; try { incA.decryptTo(dA, key, null); } catch (Exception e) { threwA = true; }
        check("flipped ciphertext bit -> rejected, no output file", threwA && !dA.exists());

        // 3b: swap two chunks' positions (same ciphertext, header index changed)
        List<SecureFrame> sw = new ArrayList<>(frames2);
        SecureFrame c1 = sw.get(1), c2 = sw.get(2);
        sw.set(1, new SecureFrame(c1.type, c1.transferId, c2.index, c1.total, c1.sealed));
        sw.set(2, new SecureFrame(c2.type, c2.transferId, c1.index, c2.total, c2.sealed));
        IncomingFile incB = assemble(sw);
        File dB = File.createTempFile("qc_t_", ".bin");
        boolean threwB = false; try { incB.decryptTo(dB, key, null); } catch (Exception e) { threwB = true; }
        check("reordered chunks (header index rewritten) -> rejected", threwB && !dB.exists());

        // 3c: splice a chunk from ANOTHER transfer into this one
        File f3 = randomFile(70_000);
        alice.sendFile(f3, null);
        List<SecureFrame> other = receive(atBob, 4);
        List<SecureFrame> sp = new ArrayList<>(frames2);
        SecureFrame foreign = other.get(2);
        sp.set(2, new SecureFrame(foreign.type, frames2.get(2).transferId, foreign.index, foreign.total, foreign.sealed));
        IncomingFile incC = assemble(sp);
        File dC = File.createTempFile("qc_t_", ".bin");
        boolean threwC = false; try { incC.decryptTo(dC, key, null); } catch (Exception e) { threwC = true; }
        check("chunk spliced in from a different transfer -> rejected", threwC && !dC.exists());

        // 3d: header total rewritten (truncation attempt)
        SecureFrame m = frames2.get(0);
        SecureFrame forged = new SecureFrame(m.type, m.transferId, m.index, m.total - 1, m.sealed);
        check("meta frame with altered chunk count fails authentication", forged.tryOpen(key) == null);

        System.out.println("== 4. Incomplete transfers ==");
        IncomingFile partial = new IncomingFile(frames2.get(0).transferId);
        for (int i = 0; i < 3; i++) partial.accept(frames2.get(i));   // meta + 2 of 3 chunks
        check("missing chunk -> isComplete() == false (" + partial.receivedChunks() + "/" + partial.expectedChunks() + ")", !partial.isComplete());
        boolean threwP = false; try { partial.decryptTo(File.createTempFile("qc_p_", ".bin"), key, null); } catch (Exception e) { threwP = true; }
        check("decryptTo on incomplete file throws", threwP);
        check("duplicate frame ignored", !partial.accept(frames2.get(1)));
        SecureFrame absurd = new SecureFrame(SecureFrame.FILE_CHUNK, frames2.get(0).transferId, 0, 5_000_000, frames2.get(1).sealed);
        check("absurd chunk count rejected", !new IncomingFile(frames2.get(0).transferId).accept(absurd));

        System.out.println("== 5. Limits ==");
        File big = File.createTempFile("qc_big_", ".bin"); big.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(big, "rw")) { raf.setLength(ChatConnection.MAX_FILE_BYTES + 1); }
        boolean tooBig = false; try { alice.sendFile(big, null); } catch (IOException e) { tooBig = true; }
        check("file over 64 MB refused before anything is sent", tooBig && atBob.frames.isEmpty());
        boolean notFile = false; try { alice.sendFile(new File("/tmp"), null); } catch (IOException e) { notFile = true; }
        check("directory refused", notFile);
        boolean longText = false; try { alice.sendText(new String(new char[1_100_000]).replace('\0', 'x')); } catch (IOException e) { longText = true; }
        check("oversized text refused", longText);

        System.out.println("== 6. Text frames ==");
        alice.sendText("héllo wörld \u2713 日本語");
        SecureFrame tf = receive(atBob, 1).get(0);
        check("text decrypts with right key (unicode ok)", "héllo wörld \u2713 日本語".equals(new String(tf.tryOpen(key), "UTF-8")));
        check("text does not decrypt with wrong key", tf.tryOpen(wrong) == null);
        check("frame survives encode/decode", Arrays.equals(SecureFrame.decode(tf.encode()).sealed, tf.sealed));
        boolean junk = false; try { SecureFrame.decode(new byte[5]); } catch (IOException e) { junk = true; }
        check("malformed frame rejected by decode()", junk);

        System.out.println("== 7. Text interleaved with a file transfer (two threads) ==");
        File f7 = randomFile(500_000);
        Thread sender = new Thread(() -> { try { alice.sendFile(f7, null); } catch (Exception e) { e.printStackTrace(); } });
        sender.start();
        for (int i = 0; i < 5; i++) { alice.sendText("ping " + i); Thread.sleep(2); }
        sender.join();
        int expect = 1 + (500_000 + ChatConnection.CHUNK_SIZE - 1) / ChatConnection.CHUNK_SIZE + 5;
        List<SecureFrame> mixed = receive(atBob, expect);
        Map<Integer, IncomingFile> byId = new HashMap<>();
        int texts = 0;
        for (SecureFrame f : mixed) {
            if (f.type == SecureFrame.TEXT) { texts++; continue; }
            byId.computeIfAbsent(f.transferId, IncomingFile::new).accept(f);
        }
        IncomingFile only = byId.values().iterator().next();
        File dst7 = File.createTempFile("qc_i_", ".bin");
        only.decryptTo(dst7, key, null);
        check("5 texts + 1 file all arrive; file intact (" + texts + " texts)",
                texts == 5 && byId.size() == 1 && Arrays.equals(Files.readAllBytes(f7.toPath()), Files.readAllBytes(dst7.toPath())));

        System.out.println("== 8. Peer hang-up is reported ==");
        alice.close();
        long end = System.currentTimeMillis() + 3000;
        while (!atBob.disconnected && System.currentTimeMillis() < end) Thread.sleep(20);
        check("receiver notified when the other side closes", atBob.disconnected);
        bob.close();

        System.out.println("== 9. Over a real TCP socket (2 MB) ==");
        try (ServerSocket ss = new ServerSocket(0)) {
            int port = ss.getLocalPort();
            Collector atServer = new Collector();
            Thread srv = new Thread(() -> { try { Socket s = ss.accept(); new ChatConnection(new SocketMessageTransport(s), key).startListening(atServer); } catch (Exception e) { e.printStackTrace(); } });
            srv.start();
            Socket cs = new Socket("localhost", port);
            ChatConnection client = new ChatConnection(new SocketMessageTransport(cs), key);
            File f9 = randomFile(2_000_000);
            client.sendFile(f9, null);
            int chunks9 = (2_000_000 + ChatConnection.CHUNK_SIZE - 1) / ChatConnection.CHUNK_SIZE;
            IncomingFile inc9 = assemble(receive(atServer, chunks9 + 1));
            File d9 = File.createTempFile("qc_s_", ".bin");
            inc9.decryptTo(d9, key, null);
            check("2 MB file intact over TCP (" + chunks9 + " chunks)", Arrays.equals(Files.readAllBytes(f9.toPath()), Files.readAllBytes(d9.toPath())));
            client.close();
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL FILE-TRANSFER TESTS PASSED" : (failures + " TEST(S) FAILED"));
        System.exit(failures == 0 ? 0 : 1);
    }
}
