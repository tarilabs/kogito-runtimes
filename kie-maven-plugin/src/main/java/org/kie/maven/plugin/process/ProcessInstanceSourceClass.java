package org.kie.maven.plugin.process;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.javaparser.ast.CompilationUnit;
import org.drools.javaparser.ast.Modifier;
import org.drools.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.drools.javaparser.ast.expr.MethodCallExpr;
import org.drools.javaparser.ast.expr.NameExpr;
import org.drools.javaparser.ast.expr.NullLiteralExpr;
import org.drools.javaparser.ast.stmt.BlockStmt;
import org.drools.javaparser.ast.stmt.ReturnStmt;
import org.drools.javaparser.ast.type.ClassOrInterfaceType;
import org.kie.api.definition.process.Process;
import org.kie.api.runtime.process.ProcessRuntime;
import org.kie.submarine.process.impl.AbstractProcessInstance;

public class ProcessInstanceSourceClass {

    private final String packageName;
    private final String typeName;
    private final String modelTypeName;
    private final String canonicalName;
    private final String targetTypeName;
    private final String targetCanonicalName;
    private final String generatedFilePath;
    private final String completePath;

    public static String qualifiedName(String packageName, String typeName) {
        return packageName + "." + typeName + "ProcessInstance";
    }

    public ProcessInstanceSourceClass(String packageName, String typeName, String modelTypeName) {
        this.packageName = packageName;
        this.typeName = typeName;
        this.modelTypeName = modelTypeName;
        this.canonicalName = packageName + "." + typeName;
        this.targetTypeName = typeName + "ProcessInstance";
        this.targetCanonicalName = packageName + "." + targetTypeName;
        this.generatedFilePath = targetCanonicalName.replace('.', '/') + ".java";
        this.completePath = "src/main/java/" + generatedFilePath;
    }

    public void write(MemoryFileSystem srcMfs) {
        srcMfs.write(completePath, generate().getBytes());
    }

    public String generate() {
        return compilationUnit().toString();
    }

    public CompilationUnit compilationUnit() {
        CompilationUnit compilationUnit = new CompilationUnit(packageName);
        compilationUnit.getTypes().add(classDeclaration());
        return compilationUnit;
    }

    public ClassOrInterfaceDeclaration classDeclaration() {
        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration()
                .setName(targetTypeName)
                .addModifier(Modifier.Keyword.PUBLIC);
        classDecl
                .addExtendedType(
                        new ClassOrInterfaceType(null, AbstractProcessInstance.class.getCanonicalName())
                                .setTypeArguments(new ClassOrInterfaceType(null, modelTypeName)))
                .addConstructor(Modifier.Keyword.PUBLIC)
                .addParameter(ProcessSourceClass.processType(modelTypeName), "process")
                .addParameter(modelTypeName, "value")
                .addParameter(ProcessRuntime.class.getCanonicalName(), "processRuntime")
                .setBody(new BlockStmt().addStatement(new MethodCallExpr(
                        "super",
                        new NameExpr("process"),
                        new NameExpr("value"),
                        new NameExpr("processRuntime")
                )));
        classDecl.addMethod("legacyProcess", Modifier.Keyword.PUBLIC)
                .setType(Process.class.getCanonicalName())
                .setBody(new BlockStmt().addStatement(new ReturnStmt(new NullLiteralExpr())));
        return classDecl;
    }

    public String targetTypeName() {
        return targetTypeName;
    }

    public String generatedFilePath() {
        return generatedFilePath;
    }
}
