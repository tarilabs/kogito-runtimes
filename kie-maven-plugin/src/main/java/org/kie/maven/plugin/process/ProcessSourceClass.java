package org.kie.maven.plugin.process;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.javaparser.ast.CompilationUnit;
import org.drools.javaparser.ast.Modifier;
import org.drools.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.drools.javaparser.ast.body.MethodDeclaration;
import org.drools.javaparser.ast.expr.MethodCallExpr;
import org.drools.javaparser.ast.expr.NameExpr;
import org.drools.javaparser.ast.expr.ObjectCreationExpr;
import org.drools.javaparser.ast.expr.ThisExpr;
import org.drools.javaparser.ast.stmt.BlockStmt;
import org.drools.javaparser.ast.stmt.ReturnStmt;
import org.drools.javaparser.ast.type.ClassOrInterfaceType;
import org.jbpm.process.instance.LightProcessRuntime;
import org.kie.submarine.process.Process;
import org.kie.submarine.process.impl.AbstractProcess;

import static org.drools.javaparser.ast.NodeList.nodeList;

public class ProcessSourceClass {

    private final String packageName;
    private final String typeName;
    private final String modelTypeName;
    private final String generatedFilePath;
    private final String completePath;
    private final String canonicalName;
    private final String targetCanonicalName;
    private String targetTypeName;
    private boolean hasCdi = false;

    public ProcessSourceClass(String packageName, String typeName, String modelTypeName) {
        this.packageName = packageName;
        this.typeName = typeName;
        this.modelTypeName = modelTypeName;
        this.canonicalName = packageName + "." + typeName;
        this.targetTypeName = typeName + "Process_";
        this.targetCanonicalName = packageName + "." + targetTypeName;
        this.generatedFilePath = targetCanonicalName.replace('.', '/') + ".java";
        this.completePath = "src/main/java/" + generatedFilePath;
    }

    public String targetCanonicalName() {
        return targetCanonicalName;
    }

    public String targetTypeName() {
        return targetTypeName;
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

    private MethodDeclaration createInstanceMethod(String processInstanceFQCN) {
        MethodDeclaration methodDeclaration = new MethodDeclaration();

        ReturnStmt returnStmt = new ReturnStmt(
                new ObjectCreationExpr()
                        .setType(processInstanceFQCN)
                        .setArguments(nodeList(
                                new ThisExpr(),
                                new NameExpr("value"),
                                createProcessRuntime())));

        methodDeclaration.setName("createInstance")
                .addModifier(Modifier.Keyword.PUBLIC)
                .addParameter(modelTypeName, "value")
                .setType(processInstanceFQCN)
                .setBody(new BlockStmt()
                                 .addStatement(returnStmt));
        return methodDeclaration;
    }

    private MethodCallExpr createProcessRuntime() {
        return new MethodCallExpr(
                new NameExpr(LightProcessRuntime.class.getCanonicalName()),
                "ofProcess").addArgument(new MethodCallExpr(
                        new NameExpr(typeName+"Process"), "process"));
    }

    public static ClassOrInterfaceType processType(String canonicalName) {
        return new ClassOrInterfaceType(null, Process.class.getCanonicalName())
                .setTypeArguments(new ClassOrInterfaceType(null, canonicalName));
    }

    public static ClassOrInterfaceType abstractProcessType(String canonicalName) {
        return new ClassOrInterfaceType(null, AbstractProcess.class.getCanonicalName())
                .setTypeArguments(new ClassOrInterfaceType(null, canonicalName));
    }

    public ClassOrInterfaceDeclaration classDeclaration() {
        ClassOrInterfaceDeclaration cls = new ClassOrInterfaceDeclaration()
                .setName(targetTypeName)
                .setModifiers(Modifier.Keyword.PUBLIC);
        if (hasCdi) {
            cls.addAnnotation("javax.enterprise.context.ApplicationScoped");
        }
        String processInstanceFQCN = ProcessInstanceSourceClass.qualifiedName(packageName, typeName);

        MethodDeclaration methodDeclaration = createInstanceMethod(processInstanceFQCN);
        cls.addExtendedType(abstractProcessType(modelTypeName))
                .addMember(methodDeclaration);
        return cls;
    }

    public ProcessSourceClass withCdi(boolean hasCdi) {
        this.hasCdi = true;
        return this;
    }

    public String generatedFilePath() {
        return generatedFilePath;
    }
}
