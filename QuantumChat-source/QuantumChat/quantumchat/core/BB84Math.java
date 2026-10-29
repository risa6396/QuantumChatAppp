package quantumchat.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure BB84 primitives: random bit/basis generation, simulated qubit
 * measurement, sifting, and index bookkeeping. Every method takes an
 * explicit {@link Random} rather than holding its own, so a run can be made
 * fully reproducible by seeding {@link QKDConfig} -- useful for testing the
 * framework itself.
 *
 * NOTE ON SIMULATION: BB84's security comes from physics -- measuring an
 * unknown quantum state in the wrong basis irreversibly disturbs it. A
 * classical computer can't carry a real qubit, so {@link #measure} simulates
 * only the *statistics* predicted by quantum mechanics: a measurement in
 * the correct basis is deterministic, a measurement in the wrong basis
 * returns a uniformly random bit. That's enough to make sifting, QBER
 * estimation, and eavesdropper detection behave exactly as they would with
 * real hardware.
 */
public final class BB84Math {

    private BB84Math() { }

    public static int[] randomBits(int n, Random rng) {
        int[] bits = new int[n];
        for (int i = 0; i < n; i++) {
            bits[i] = rng.nextInt(2);
        }
        return bits;
    }

    public static int[] randomBases(int n, Random rng) {
        int[] bases = new int[n];
        for (int i = 0; i < n; i++) {
            bases[i] = rng.nextInt(2);
        }
        return bases;
    }

    public static int measure(int srcBit, int srcBasis, int measBasis, Random rng) {
        if (srcBasis == measBasis) {
            return srcBit;
        }
        return rng.nextInt(2);
    }

    public static int[] measureAll(int[] srcBits, int[] srcBases, int[] measBases, Random rng) {
        int n = srcBits.length;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = measure(srcBits[i], srcBases[i], measBases[i], rng);
        }
        return result;
    }

    /** Indices where two basis-choice arrays agree ("sifting"). */
    public static int[] matchingIndices(int[] basesA, int[] basesB) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < basesA.length; i++) {
            if (basesA[i] == basesB[i]) {
                idx.add(i);
            }
        }
        int[] out = new int[idx.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = idx.get(i);
        }
        return out;
    }

    /** Selects the values at the given indices, preserving order. */
    public static int[] select(int[] source, int[] indices) {
        int[] out = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            out[i] = source[indices[i]];
        }
        return out;
    }

    /** A random permutation of 0..n-1. */
    public static int[] shuffledIndices(int n, Random rng) {
        List<Integer> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(i);
        }
        Collections.shuffle(list, rng);
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /** All indices in [0, n) not present in {@code exclude}, ascending order. */
    public static int[] complement(int n, int[] exclude) {
        boolean[] excluded = new boolean[n];
        for (int e : exclude) {
            excluded[e] = true;
        }
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!excluded[i]) {
                list.add(i);
            }
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
