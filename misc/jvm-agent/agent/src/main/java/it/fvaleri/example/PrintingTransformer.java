package it.fvaleri.example;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import static java.lang.String.format;

public class PrintingTransformer implements ClassFileTransformer {
    private final String newContent;

    public PrintingTransformer(String newContent) {
        this.newContent = newContent;
    }

    @Override
    public byte[] transform(ClassLoader loader, 
                            String className, 
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, 
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        byte[] byteCode = classfileBuffer;
        if (className.contains("it/fvaleri/example/SamplePrinter") && classBeingRedefined == null) {
            try {
                
                ClassPool cp = ClassPool.getDefault();
                CtClass cc = cp.get("it.fvaleri.example.SamplePrinter");
                CtMethod m = cc.getDeclaredMethod("printLine");
                m.setBody(format("System.out.println(\"%s\");", newContent));
                byteCode = cc.toBytecode();
                cc.detach();
            } catch (Throwable e) {
                e.printStackTrace();
                throw new IllegalClassFormatException("An error occurred on formatting class");
            }
        }
        return byteCode;
    }
}
