package com.noobk.spmscavenger.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Build-time gate that resolves packaged validation {@code @Inject} selectors against the actual
 * remapped production/Minecraft bytecode used by the validation artifact.
 *
 * <p>This intentionally audits annotations from the remapped validation JAR. A source-text check
 * cannot detect the failure where a readable Goal override survives packaging but the target method
 * is intermediary-named.</p>
 */
public final class RequiredMixinTargetAudit {

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String CONFIG = "spmscavenger.validation.mixins.json";
    private static final Pattern MIXIN_ARRAY = Pattern.compile(
            "\\\"mixins\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern QUOTED = Pattern.compile("\\\"([^\\\"]+)\\\"");
    private static final Set<String> REQUIRED_LIVENESS_MIXINS = Set.of(
            "V4TradeGoalLivenessMixin",
            "V4BackpackLivenessMixin",
            "V4TradeMarketCooldownMixin",
            "V4EntityQueryLivenessMixin",
            "V4VanillaBoardLivenessMixin",
            "V4GatherHandoffLivenessMixin",
            "V4MandatoryHandoffLivenessMixin",
            "V4RouteEvidenceLivenessMixin",
            "V4TradeClaimLivenessMixin");

    private RequiredMixinTargetAudit() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "usage: RequiredMixinTargetAudit <production.jar> <validation.jar> "
                            + "<intermediary-minecraft.jar>...");
        }
        Path productionJar = Path.of(args[0]);
        Path validationJar = Path.of(args[1]);
        List<Path> runtimeJars = new ArrayList<>();
        runtimeJars.add(productionJar);
        for (int i = 2; i < args.length; i++) {
            runtimeJars.add(Path.of(args[i]));
        }
        AuditResult result = audit(validationJar, runtimeJars);
        result.lines().forEach(System.out::println);
        System.out.println("validationRequiredMixinTargets="
                + (result.failures().isEmpty() ? "PASS" : "FAIL"));
        System.out.println("requiredInjectorsAudited=" + result.injectorsAudited());
        System.out.println("unresolvedRequiredInjectors=" + result.failures().size());
        if (!result.failures().isEmpty()) {
            throw new IllegalStateException("Required validation Mixin targets unresolved:\n  "
                    + String.join("\n  ", result.failures()));
        }
    }

    static AuditResult audit(Path validationJar, List<Path> runtimeJars) throws IOException {
        List<String> failures = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        List<String> configuredMixins;
        try (ZipFile validation = new ZipFile(validationJar.toFile())) {
            ZipEntry configEntry = validation.getEntry(CONFIG);
            if (configEntry == null) {
                throw new IOException("validation JAR lacks " + CONFIG);
            }
            String config = new String(
                    validation.getInputStream(configEntry).readAllBytes(), StandardCharsets.UTF_8);
            if (!config.matches("(?s).*\\\"required\\\"\\s*:\\s*true.*")
                    || !config.matches("(?s).*\\\"defaultRequire\\\"\\s*:\\s*1.*")) {
                failures.add("validation Mixin config is not strict (required=true, defaultRequire=1)");
            }
            configuredMixins = configuredMixins(config);
            for (String required : REQUIRED_LIVENESS_MIXINS) {
                if (!configuredMixins.contains(required)) {
                    failures.add("required liveness Mixin missing from config: " + required);
                }
            }
        }

        int injectors = 0;
        for (String mixinName : configuredMixins) {
            String mixinEntry = "com/noobk/spmscavenger/validation/mixin/"
                    + mixinName + ".class";
            byte[] mixinBytes = readEntry(validationJar, mixinEntry);
            if (mixinBytes == null) {
                failures.add("configured Mixin class missing from validation JAR: " + mixinEntry);
                continue;
            }
            ClassMetadata mixin = readClass(mixinBytes);
            if (mixin.mixinTargets().size() != 1) {
                failures.add(mixinName + " must declare exactly one class target, found "
                        + mixin.mixinTargets());
                continue;
            }
            String targetName = mixin.mixinTargets().iterator().next();
            LocatedClass target = locateClass(runtimeJars, targetName + ".class");
            if (target == null) {
                failures.add(mixinName + " target class is absent from remapped runtime JARs: "
                        + targetName);
                continue;
            }
            ClassMetadata targetMetadata = readClass(target.bytes());
            for (Injector injector : mixin.injectors()) {
                injectors++;
                if (injector.require() != null && injector.require() == 0) {
                    failures.add(mixinName + "#" + injector.handler()
                            + " explicitly sets require=0");
                    continue;
                }
                Set<MethodSignature> matches = resolve(
                        targetMetadata.methods(), injector.selectors());
                if (matches.size() != 1) {
                    failures.add(mixinName + "#" + injector.handler()
                            + " selectors=" + injector.selectors()
                            + " resolve to " + matches + " in " + targetName
                            + " from " + target.jar().getFileName());
                    continue;
                }
                MethodSignature matched = matches.iterator().next();
                String ownership = ownership(targetName, matched.name(), injector.selectors());
                lines.add("MIXIN_TARGET PASS " + mixinName + "#" + injector.handler()
                        + " -> " + targetName + "::" + matched.name() + matched.descriptor()
                        + " ownership=" + ownership
                        + " jar=" + target.jar().getFileName());
            }
        }
        return new AuditResult(injectors, List.copyOf(lines), List.copyOf(failures));
    }

    static Set<MethodSignature> resolve(
            Collection<MethodSignature> targetMethods, Collection<String> selectors) {
        Set<MethodSignature> matches = new LinkedHashSet<>();
        for (String selector : selectors) {
            ParsedSelector parsed = ParsedSelector.parse(selector);
            for (MethodSignature method : targetMethods) {
                if (method.name().equals(parsed.name())
                        && (parsed.descriptor() == null
                        || method.descriptor().equals(parsed.descriptor()))) {
                    matches.add(method);
                }
            }
        }
        return matches;
    }

    private static String ownership(
            String targetName, String matchedName, Collection<String> selectors) {
        if (targetName.startsWith("net/minecraft/")) {
            return "MINECRAFT";
        }
        if (matchedName.startsWith("method_") && selectors.stream().anyMatch(s -> {
            String name = ParsedSelector.parse(s).name();
            return !name.startsWith("method_");
        })) {
            return "MINECRAFT_OVERRIDE";
        }
        return "MOD_OWNED";
    }

    private static List<String> configuredMixins(String config) {
        Matcher array = MIXIN_ARRAY.matcher(config);
        if (!array.find()) {
            throw new IllegalArgumentException("validation Mixin config has no mixins array");
        }
        List<String> result = new ArrayList<>();
        Matcher quoted = QUOTED.matcher(array.group(1));
        while (quoted.find()) {
            result.add(quoted.group(1));
        }
        return result;
    }

    private static LocatedClass locateClass(List<Path> jars, String entryName) throws IOException {
        for (Path jar : jars) {
            byte[] bytes = readEntry(jar, entryName);
            if (bytes != null) {
                return new LocatedClass(jar, bytes);
            }
        }
        return null;
    }

    private static byte[] readEntry(Path jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream input = zip.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }

    private static ClassMetadata readClass(byte[] bytes) {
        Set<String> targets = new LinkedHashSet<>();
        Set<MethodSignature> methods = new LinkedHashSet<>();
        List<Injector> injectors = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN.equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!"value".equals(name)) {
                            return null;
                        }
                        return collectingTypes(targets::add);
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                methods.add(new MethodSignature(name, descriptor));
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(
                            String annotationDescriptor, boolean visible) {
                        if (!INJECT.equals(annotationDescriptor)) {
                            return null;
                        }
                        List<String> selectors = new ArrayList<>();
                        Integer[] require = new Integer[1];
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String field, Object value) {
                                if ("require".equals(field) && value instanceof Integer count) {
                                    require[0] = count;
                                }
                            }

                            @Override
                            public AnnotationVisitor visitArray(String field) {
                                if (!"method".equals(field)) {
                                    return null;
                                }
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String ignored, Object value) {
                                        selectors.add(String.valueOf(value));
                                    }
                                };
                            }

                            @Override
                            public void visitEnd() {
                                injectors.add(new Injector(name, List.copyOf(selectors), require[0]));
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassMetadata(Set.copyOf(targets), Set.copyOf(methods), List.copyOf(injectors));
    }

    private static AnnotationVisitor collectingTypes(Consumer<String> sink) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String ignored, Object value) {
                if (value instanceof Type type) {
                    sink.accept(type.getInternalName());
                }
            }
        };
    }

    record MethodSignature(String name, String descriptor) {
    }

    record AuditResult(int injectorsAudited, List<String> lines, List<String> failures) {
    }

    private record ClassMetadata(
            Set<String> mixinTargets, Set<MethodSignature> methods, List<Injector> injectors) {
    }

    private record Injector(String handler, List<String> selectors, Integer require) {
    }

    private record LocatedClass(Path jar, byte[] bytes) {
    }

    private record ParsedSelector(String name, String descriptor) {
        private static ParsedSelector parse(String selector) {
            String unowned = selector;
            int ownerEnd = selector.indexOf(';');
            if (selector.startsWith("L") && ownerEnd >= 0) {
                unowned = selector.substring(ownerEnd + 1);
            }
            int descriptorAt = unowned.indexOf('(');
            if (descriptorAt < 0) {
                return new ParsedSelector(unowned, null);
            }
            return new ParsedSelector(
                    unowned.substring(0, descriptorAt), unowned.substring(descriptorAt));
        }
    }
}
