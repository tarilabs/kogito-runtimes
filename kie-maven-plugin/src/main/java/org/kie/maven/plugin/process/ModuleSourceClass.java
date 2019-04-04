package org.kie.maven.plugin.process;

import java.util.ArrayList;
import java.util.List;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.javaparser.ast.CompilationUnit;
import org.drools.javaparser.ast.Modifier;
import org.drools.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.drools.javaparser.ast.body.MethodDeclaration;
import org.drools.javaparser.ast.expr.ObjectCreationExpr;
import org.drools.javaparser.ast.stmt.BlockStmt;
import org.drools.javaparser.ast.stmt.ReturnStmt;
import org.drools.javaparser.ast.type.ClassOrInterfaceType;
import org.drools.modelcompiler.builder.generator.RuleUnitInstanceSourceClass;
import org.drools.modelcompiler.builder.generator.RuleUnitSourceClass;
import org.kie.submarine.rules.RuleUnit;

public class ModuleSourceClass {

    private final String packageName;
    private final String sourceFilePath;
    private final String completePath;
    private final String targetCanonicalName;
    private final List<RuleUnitSourceClass> processes;
    private final List<RuleUnitInstanceSourceClass> processInstances;
    private String targetTypeName;
    private boolean hasCdi;

    public ModuleSourceClass() {
        this.packageName = "org.drools.project.model";
        this.targetTypeName = "Module";
        this.targetCanonicalName = packageName + "." + targetTypeName;
        this.sourceFilePath = targetCanonicalName.replace('.', '/') + ".java";
        this.completePath = "src/main/java/" + sourceFilePath;
        this.processes = new ArrayList<>();
        this.processInstances = new ArrayList<>();
    }

    public void addRuleUnit(RuleUnitSourceClass rusc) {
        processes.add(rusc);
    }

    public void addRuleUnitInstance(RuleUnitInstanceSourceClass ruisc) {
        processInstances.add(ruisc);
    }

    public void write(MemoryFileSystem srcMfs) {
        processes.forEach(r -> r.withCdi(hasCdi).write(srcMfs));
        processInstances.forEach(r -> r.write(srcMfs));
        srcMfs.write(completePath, generate().getBytes());
    }

    public String generate() {
        return compilationUnit().toString();
    }

    public CompilationUnit compilationUnit() {
        CompilationUnit compilationUnit = new CompilationUnit(packageName);
        ClassOrInterfaceDeclaration cls =
                compilationUnit.addClass(targetTypeName);

        for (RuleUnitSourceClass r : processes) {
            cls.addMember(ruleUnitFactoryMethod(r));
        }

        return compilationUnit;
    }

    public static MethodDeclaration ruleUnitFactoryMethod(RuleUnitSourceClass r) {
        return new MethodDeclaration()
                .addModifier(Modifier.Keyword.PUBLIC)
                .setName("create" + r.targetTypeName())
                .setType(r.targetCanonicalName())
                .setBody(new BlockStmt().addStatement(new ReturnStmt(
                        new ObjectCreationExpr()
                                .setType(r.targetCanonicalName()))));
    }

    public static ClassOrInterfaceType ruleUnitType(String canonicalName) {
        return new ClassOrInterfaceType(null, RuleUnit.class.getCanonicalName())
                .setTypeArguments(new ClassOrInterfaceType(null, canonicalName));
    }

    public ModuleSourceClass withCdi(boolean hasCdi) {
        this.hasCdi = hasCdi;
        return this;
    }
}
