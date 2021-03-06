/*
 * Copyright 2013 Peter Lawrey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.lang.testing;

import net.openhft.lang.affinity.PosixJNAAffinity;

import java.io.PrintStream;

/**
 * User: peter
 * Date: 30/06/13
 * Time: 13:13
 */
public class MicroJitterSampler {
    static final boolean BINDING;

    static {
        boolean binding = false;
        try {
            PosixJNAAffinity.INSTANCE.getcpu();
            binding = true;
        } catch (Throwable ignored) {
        }
        System.out.println("Binding: " + binding);
        BINDING = binding;

    }

    static final long[] DELAY = {
            2 * 1000, 3 * 1000, 4 * 1000, 6 * 1000, 8 * 1000, 10 * 1000, 14 * 1000,
            20 * 1000, 30 * 1000, 40 * 1000, 60 * 1000, 80 * 1000, 100 * 1000, 140 * 1000,
            200 * 1000, 300 * 1000, 400 * 1000, 600 * 1000, 800 * 1000, 1000 * 1000,
            2 * 1000 * 1000, 5 * 1000 * 1000, 10 * 1000 * 10000,
            20 * 1000 * 1000, 50 * 1000 * 1000, 100 * 1000 * 1000
    };
    static final double UTIL = Double.parseDouble(System.getProperty("util", "50"));
    static final int CPU = Integer.getInteger("cpu", 0);
    final int[] count = new int[DELAY.length];
    long totalTime = 0;

    public static void main(String... ignored) throws InterruptedException {
        if (BINDING)
            if (CPU > 0) {
                PosixJNAAffinity.INSTANCE.setAffinity(1L << CPU);
            } else {
                // anything but cpu 0
                PosixJNAAffinity.INSTANCE.setAffinity(PosixJNAAffinity.INSTANCE.getAffinity() & ~1);
            }
        // warmup.
        new MicroJitterSampler().sample(1000 * 1000 * 1000);

        MicroJitterSampler microJitterSampler = new MicroJitterSampler();
        while (!Thread.currentThread().isInterrupted()) {
            if (UTIL >= 100) {
                microJitterSampler.sample(30L * 1000 * 1000 * 1000);
            } else {
                long sampleLength = (long) ((1 / (1 - UTIL / 100) - 1) * 1000 * 1000);
                for (int i = 0; i < 30 * 1000; i += 2) {
                    microJitterSampler.sample(sampleLength);
                    Thread.sleep(1);
                }
            }
            if (BINDING)
                System.out.println("On cpu " + PosixJNAAffinity.INSTANCE.getcpu());
            microJitterSampler.print(System.out);
        }
    }

    public void sample(long intervalNS) {
        long prev = System.nanoTime();
        long end = prev + intervalNS;
        long now;
        do {
            now = System.nanoTime();
            long time = now - prev;
            if (time >= DELAY[0]) {
                int i;
                for (i = 1; i < DELAY.length; i++)
                    if (time < DELAY[i])
                        break;
                count[i - 1]++;
            }
            prev = now;
        } while (now < end);
        totalTime += intervalNS;
    }

    public void print(PrintStream ps) {
        ps.println("After " + totalTime / 1000000000 + " seconds, the average per hour was");
        for (int i = 0; i < DELAY.length; i++) {
            if (count[i] < 1) continue;
            long countPerHour = (long) Math.ceil(count[i] * 3600e9 / totalTime);
            ps.println(asString(DELAY[i]) + "\t" + countPerHour);
        }
        ps.println();
    }

    private String asString(long timeNS) {
        return timeNS < 1000 ? timeNS + "ns" :
                timeNS < 1000000 ? timeNS / 1000 + "us" :
                        timeNS < 1000000000 ? timeNS / 1000000 + "ms" :
                                timeNS / 1000000000 + "sec";
    }
}
