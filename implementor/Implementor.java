package info.kgeorgiy.ja.gordienko.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class Implementor implements Impler {

    private static final String SPACE = " ";
    private static final String INDENT = "\t";
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private String currIndent;
    private StringBuilder classImpl;

    private String getDefaultValue(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return "null";
        }
        if (clazz.equals(void.class)) {
            return "";
        }
        if (clazz.equals(boolean.class)) {
            return "false";
        }
        return "0";
    }

    private String updateIndent(String bracket) {
        if (bracket.equals("{")) {
            currIndent += INDENT;
            return SPACE + bracket;
        } else {
            currIndent = currIndent.substring(0, currIndent.length() - 1);
            return currIndent + bracket;
        }
    }

    private void generatePackageDeclaration(String packageName) {
        if (!packageName.isEmpty()) {
            classImpl.append("package ").append(packageName).append(";").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }
    }

    private void generateClassDeclaration(Class<?> token, String classImplName) {
        classImpl.append("public class ").append(classImplName).append(" implements ").append(token.getCanonicalName())
                .append(updateIndent("{")).append(LINE_SEPARATOR);
    }

    private void implementMethod(Method m) {
        String modifiers = Modifier.toString(m.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT);

        Class<?> methodReturnType = m.getReturnType();

        classImpl.append(LINE_SEPARATOR).append(currIndent).append("@Override").append(LINE_SEPARATOR);

        classImpl.append(currIndent)
                .append(modifiers)
                .append(SPACE)
                .append(methodReturnType.getCanonicalName())
                .append(SPACE).append(m.getName()).append("(");

        Parameter[] params = m.getParameters();

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            classImpl.append(p.getType().getCanonicalName())
                    .append(SPACE)
                    .append(p.getName())
                    .append(i == params.length - 1 ? "" : ", ");
        }

        classImpl.append(")");

        Class<?>[] exceptionsToBeThrown = m.getExceptionTypes();
        if (exceptionsToBeThrown.length != 0) {
            classImpl.append(" throws ");
        }

        for (int i = 0; i < exceptionsToBeThrown.length; i++) {
            Class<?> exception = exceptionsToBeThrown[i];
            classImpl.append(exception.getCanonicalName())
                    .append(i == exceptionsToBeThrown.length - 1 ? "" : ", ");
        }

        classImpl.append(updateIndent("{")).append(LINE_SEPARATOR);

        classImpl.append(currIndent)
                .append("return ")
                .append(getDefaultValue(methodReturnType))
                .append(";");

        classImpl.append(LINE_SEPARATOR)
                .append(updateIndent("}"))
                .append(LINE_SEPARATOR)
                .append(LINE_SEPARATOR);
    }

    private void implementMethods(Class<?> token) {
        Method[] ifaceMethods = token.getMethods();
        for (Method m : ifaceMethods) {
            if (Modifier.isStatic(m.getModifiers()) || !Modifier.isAbstract(m.getModifiers())) {
                continue;
            }
            implementMethod(m);
        }
    }

    private void writeToFile(Path root, String packageName, String classImplName) throws ImplerException {
        Path packagePath = root;
        if (!packageName.isEmpty()) {
            packagePath = root.resolve(packageName.replace('.', File.separatorChar));
        }
        Path outputFilePath = packagePath.resolve(classImplName + ".java");

        try {
            Files.createDirectories(outputFilePath.getParent());
        } catch (IOException ignored) {
        }

        try (BufferedWriter output = Files.newBufferedWriter(outputFilePath)) {
            output.write(classImpl.toString());
        } catch (IOException e) {
            throw new ImplerException("Named file exists but is a directory rather than a regular file, " +
                    "does not exist but cannot be created, " +
                    "has special access settings, " +
                    "or cannot be opened for any other reason", e);
        }
    }


    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (token == null) {
            throw new ImplerException("Token should not be null.");
        }

        if (root == null) {
            throw new ImplerException("Root should not be null.");
        }

        if (!token.isInterface()) {
            throw new ImplerException("Passed token is not an interface.");
        }

        if (Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Cannot implement private interface.");
        }

        classImpl = new StringBuilder();
        currIndent = "";

        String ifaceSimpleName = token.getSimpleName();
        String classImplName = ifaceSimpleName + "Impl";

        Package pkg = token.getPackage();
        String packageName = (pkg == null ? "" : pkg.getName());

        generatePackageDeclaration(packageName);

        generateClassDeclaration(token, classImplName);

        implementMethods(token);

        classImpl.append(updateIndent("}"));

        writeToFile(root, packageName, classImplName);
    }

    public static void main(String[] args) {
        if (args[0] == null) {
            System.err.println("Args should not be null.");
        }

        if (args.length > 1) {
            System.err.println("Only 1 CL argument needed.");
        }

        String fullInterfaceName = args[0];

        try {
            Class<?> token = Class.forName(fullInterfaceName);
            Path root = Path.of(fullInterfaceName);
            new Implementor().implement(token, root);
        } catch (InvalidPathException e) {
            System.err.printf("Provided path '%s' is invalid", fullInterfaceName);
        } catch (ClassNotFoundException e) {
            System.err.printf("No definition for the class with the specified name '%s' could be found",
                    fullInterfaceName);
        } catch (ImplerException e) {
            System.err.printf("Exception thrown while implementing interface '%s':%n%s",
                    fullInterfaceName,
                    e.getMessage());
        }
    }
}
