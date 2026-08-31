package com.noobk.spmscavenger.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Packaged-bytecode gate for the inherited SavedData linkage failure observed in V4-G. */
public final class ValidationRuntimeLinkageAudit {

    private static final String CONTROLLER =
            "com/noobk/spmscavenger/validation/V4RuntimeCampaignController.class";
    private static final String SAVED_DATA_OWNER =
            "com/noobk/spmscavenger/village/VillageMemorySavedData";

    private ValidationRuntimeLinkageAudit() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: ValidationRuntimeLinkageAudit <remapped-validation.jar>");
        }
        List<String> failures = audit(Path.of(args[0]));
        System.out.println("validationRuntimeLinkage="
                + (failures.isEmpty() ? "PASS" : "FAIL"));
        System.out.println("unresolvedInheritedMinecraftMembers=" + failures.size());
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Unresolved validation runtime linkage:\n  "
                    + String.join("\n  ", failures));
        }
    }

    static List<String> audit(Path validationJar) throws IOException {
        List<String> failures = new ArrayList<>();
        try (ZipFile zip = new ZipFile(validationJar.toFile())) {
            ZipEntry entry = zip.getEntry(CONTROLLER);
            if (entry == null) {
                throw new IOException("validation JAR lacks " + CONTROLLER);
            }
            ClassReader reader = new ClassReader(zip.getInputStream(entry));
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String invokedName,
                                String invokedDescriptor, boolean isInterface) {
                            if (SAVED_DATA_OWNER.equals(owner)
                                    && "setDirty".equals(invokedName)
                                    && "()V".equals(invokedDescriptor)) {
                                failures.add(name + descriptor + " invokes unresolved "
                                        + owner + ".setDirty()V");
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return List.copyOf(failures);
    }
}
