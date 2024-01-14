package it.fvaleri.example;

import java.lang.instrument.Instrumentation;

public class MyAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.printf("Start agent. Args: %s%n", agentArgs);
        inst.addTransformer(new PrintingTransformer(agentArgs));
    }
}
