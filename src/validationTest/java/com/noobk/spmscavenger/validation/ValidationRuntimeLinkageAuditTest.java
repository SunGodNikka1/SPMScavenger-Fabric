package com.noobk.spmscavenger.validation;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationRuntimeLinkageAuditTest {

    private static final String CONTROLLER =
            "com/noobk/spmscavenger/validation/V4RuntimeCampaignController";

    @TempDir
    Path tempDir;

    @Test
    void rejectsTheExactStaleSubclassOwnedSetDirtySymbol() throws Exception {
        Path jar = fixtureJar(true);
        assertEquals(1, ValidationRuntimeLinkageAudit.audit(jar).size());
    }

    @Test
    void acceptsControllerWithoutTheStaleRuntimeSymbol() throws Exception {
        Path jar = fixtureJar(false);
        assertEquals(0, ValidationRuntimeLinkageAudit.audit(jar).size());
    }

    private Path fixtureJar(boolean staleCall) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CONTROLLER, null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "preparePhaseB", "()V", null, null);
        method.visitCode();
        if (staleCall) {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "com/noobk/spmscavenger/village/VillageMemorySavedData",
                    "setDirty", "()V", false);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(staleCall ? 1 : 0, 0);
        method.visitEnd();
        writer.visitEnd();

        Path jar = tempDir.resolve(staleCall ? "stale.jar" : "clean.jar");
        try (OutputStream output = Files.newOutputStream(jar);
                JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry(CONTROLLER + ".class"));
            archive.write(writer.toByteArray());
            archive.closeEntry();
        }
        return jar;
    }
}
