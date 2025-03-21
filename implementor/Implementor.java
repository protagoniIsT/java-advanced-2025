package info.kgeorgiy.ja.gordienko.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.tools.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Simple implementor of given interfaces from {@code args}.
 * @author Konstantin Gordienko
 */
public class Implementor implements Impler, JarImpler {

    /**
     * A constant that represents space symbol
     */
    private static final String SPACE = " ";

    /**
     * A constant that represents indent size
     */
    private static final int INDENT_SIZE = 4;

    /**
     * A constant that represents indent
     */
    private static final String INDENT = SPACE.repeat(INDENT_SIZE);

    /**
     * A constant that represents system-dependent line separator
     */
    private static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * A constant that represents {@code .java} file extension
     */
    private static final String FILE_TYPE_JAVA = ".java";

    /**
     * A constant that represents {@code .class} file extension
     */
    private static final String FILE_TYPE_CLASS = ".class";

    /**
     * Represents current indent based on current indentation level
     */
    private String currIndent;

    /**
     * Represents {@link StringBuilder} that contains implementation of the class/interface
     */
    private StringBuilder classImpl;

    /**
     * Default constructor
     */
    public Implementor() {

    }

    /**
     * Extracts default value of the given type
     * @param token a type
     * @return default value for given type
     */
    private String getDefaultValue(Class<?> token) {
        if (!token.isPrimitive()) {
            return "null";
        }
        if (token.equals(void.class)) {
            return "";
        }
        if (token.equals(boolean.class)) {
            return "false";
        }
        return "0";
    }

    /**
     * Updates the current indentation depending on the provided bracket
     * @param bracket bracket '{' or '}'
     * @return {@link String} containing the given bracket, preceded by the appropriate indentation
     */
    private String updateIndent(String bracket) {
        if (bracket.equals("{")) {
            currIndent += INDENT;
            return SPACE + bracket;
        } else {
            currIndent = currIndent.substring(0, currIndent.length() - INDENT_SIZE);
            return currIndent + bracket;
        }
    }

    /**
     * Generates package declaration only if {@code packageName} is not empty
     * @param packageName a package
     */
    private void generatePackageDeclaration(String packageName) {
        if (!packageName.isEmpty()) {
            classImpl.append("package ").append(packageName).append(";").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }
    }

    /**
     * Generates and writes declaration for the class
     * @param token a class/interface
     * @param classImplName desired name of the file with implemented class
     */
    private void generateClassDeclaration(Class<?> token, String classImplName) {
        classImpl.append("public class ").append(classImplName).append(" implements ").append(token.getCanonicalName())
                .append(updateIndent("{")).append(LINE_SEPARATOR);
    }

    /**
     * Implements specified method
     * @param method a {@link Method} to implement
     */
    private void implementMethod(Method method) {
        String modifiers = Modifier.toString(method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT);

        Class<?> methodReturnType = method.getReturnType();

        classImpl.append(LINE_SEPARATOR).append(currIndent).append("@Override").append(LINE_SEPARATOR);

        classImpl.append(currIndent)
                .append(modifiers)
                .append(SPACE)
                .append(methodReturnType.getCanonicalName())
                .append(SPACE).append(method.getName()).append("(");

        Parameter[] params = method.getParameters();

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            classImpl.append(p.getType().getCanonicalName())
                    .append(SPACE)
                    .append(p.getName())
                    .append(i == params.length - 1 ? "" : ", ");
        }

        classImpl.append(")");

        Class<?>[] exceptionsToBeThrown = method.getExceptionTypes();
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

    /**
     * Implements methods of the specified class/interface
     * @param token a class/interface with methods to implement
     */
    private void implementMethods(Class<?> token) {
        Method[] ifaceMethods = token.getMethods();
        for (Method m : ifaceMethods) {
            if (Modifier.isStatic(m.getModifiers()) || !Modifier.isAbstract(m.getModifiers())) {
                continue;
            }
            implementMethod(m);
        }
    }

    /**
     * Returns paths to provided classes
     * @param dependencies a list of classes to resolve paths for
     * @return {@link List} of paths
     */
    private static List<Path> getClassSourcePaths(final List<Class<?>> dependencies) {
        return dependencies.stream()
                .map(dependency -> {
                    try {
                        return Path.of(dependency.getProtectionDomain().getCodeSource().getLocation().toURI());
                    } catch (final URISyntaxException e) {
                        throw new AssertionError(e);
                    }
                })
                .toList();
    }

    /**
     * Compiles source Java code
     * @param files source files to compile
     * @param dependencies dependencies of the provided Java source code
     * @param charset encoding of the provided file
     */
    private void compile(
            final List<Path> files,
            final List<Class<?>> dependencies,
            final Charset charset
    ) {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        requireNonNull(compiler, "Could not find java compiler, include tools.jar to classpath");
        final String classpath = getClassSourcePaths(dependencies).stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        final String[] args = Stream.concat(
                Stream.of("-cp", classpath, "-encoding", charset.name()),
                files.stream().map(Path::toString)
        ).toArray(String[]::new);
        compiler.run(null, null, null, args);
    }

    /**
     * Writes interface/class implementation to the file
     * @param root root directory
     * @param packageName package to write to
     * @param classImplName name of file with implemented class
     * @throws ImplerException if error occurred while writing to file
     */
    private void writeToFile(Path root, String packageName, String classImplName) throws ImplerException {
        Path packagePath = root;
        if (!packageName.isEmpty()) {
            packagePath = root.resolve(packageName.replace('.', File.separatorChar));
        }
        Path outputFilePath = packagePath.resolve(classImplName + FILE_TYPE_JAVA);

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

    /**
     * Returns implemented class name
     * @param token implemented class/interface
     * @return {@link String} with file name with implementation
     */
    private String getImplFileName(Class<?> token) {
        return token.getSimpleName() + "Impl";
    }

    /**
     * Returns package of the specified class
     * @param token class/interface for which the package name should be determined
     * @return an empty {@link String} if no package information is found, otherwise the package name
     */
    private String getPackage(Class<?> token) {
        Package pkg = token.getPackage();
        return  (pkg == null ? "" : pkg.getName());
    }

    /**
     * Resolves the file path for a given class or interface.
     *
     * @param packageName the package name of the file (empty if the file is in the default package)
     * @param currentPath the current directory path
     * @param fileName the name of the class or interface
     * @param type the file extension or suffix (".java", ".class", ...)
     * @return resolved {@link Path}
     */
    private Path resolveFile(String packageName, Path currentPath, String fileName, String type) {
        Path pathToClass;
        if (packageName.isEmpty()) {
            pathToClass = currentPath.resolve(fileName + type);
        } else {
            pathToClass = currentPath.resolve(packageName.replace('.', File.separatorChar))
                    .resolve(fileName + type);
        }
        return pathToClass;
    }

    /**
     * Writes a new entry to a {@code .jar} file, containing specified file
     *
     * @param packageName the package name of the file (empty if the file is in the default package)
     * @param entryFileName name of the file being written
     * @param jarFile {@link Path} to the target {@code .jar} file
     * @param fileToWrite {@link Path} to source file being written to target {@code .jar} file
     * @param manifest {@link Manifest} for {@code .jar} file
     * @throws ImplerException if error occurred while writing to {@code .jar} file
     */
    private void writeToJar(String packageName,
                            String entryFileName,
                            Path jarFile,
                            Path fileToWrite,
                            Manifest manifest) throws ImplerException {
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            String entryName;
            if (packageName.isEmpty()) {
                entryName = entryFileName + FILE_TYPE_CLASS;
            } else {
                entryName = packageName.replace('.', '/') + "/" + entryFileName + FILE_TYPE_CLASS;
            }
            JarEntry jarEntry = new JarEntry(entryName);
            jarOut.putNextEntry(jarEntry);
            Files.copy(fileToWrite, jarOut);
            jarOut.closeEntry();
        } catch (IOException e) {
            throw new ImplerException("I/O error occurred while writing to .jar file", e);
        }
    }


    /**
     * Produces code implementing class or interface specified by provided {@code token}.
     * <p>
     * Generated class' name is the same as the class name of the type token with <var>Impl</var> suffix
     * added.
     *
     * @param token type token to create implementation for.
     * @param root root directory.
     * @throws ImplerException if:
     *      <ul>
     *          <li>{@code token} is {@code null}.</li>
     *          <li>{@code root} is {@code null}.</li>
     *          <li>{@code token} is not an interface.</li>
     *          <li>{@code token} is a private interface.</li>
     *          <li>An I/O error occurs while writing the implementation.</li>
     *      </ul>
     */
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

        String classImplName = getImplFileName(token);

        String packageName = getPackage(token);
        generatePackageDeclaration(packageName);

        generateClassDeclaration(token, classImplName);

        implementMethods(token);

        classImpl.append(updateIndent("}"));

        writeToFile(root, packageName, classImplName);
    }

    /**
     * Produces <var>.jar</var> file implementing class or interface specified by provided <var>token</var>.
     * <p>
     * Generated class' name is the same as the class name of the type token with <var>Impl</var> suffix
     * added.
     *
     * @param token the interface type to generate an implementation for.
     * @param jarFile the path to the {@code .jar} file where the compiled implementation will be saved.
     *
     * @throws ImplerException if:
     *      <ul>
     *          <li>{@code token} is {@code null}.</li>
     *          <li>{@code jarFile} is {@code null}.</li>
     *          <li>{@code token} is not an interface.</li>
     *          <li>{@code token} is a private interface.</li>
     *          <li>An error occurs while compiling the implementation.</li>
     *          <li>An I/O error occurs while creating the {@code .jar} file.</li>
     *      </ul>
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        if (token == null) {
            throw new ImplerException("Token should not be null.");
        }

        if (jarFile == null) {
            throw new ImplerException("JAR file path should not be null.");
        }

        if (!token.isInterface()) {
            throw new ImplerException("Passed token is not an interface.");
        }

        if (Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Cannot implement private interface.");
        }

        String classImplName = getImplFileName(token);
        String packageName = getPackage(token);
        Path currentPath = Paths.get("").toAbsolutePath();
        implement(token, currentPath);

        Path generatedClass = resolveFile(packageName, currentPath, classImplName, FILE_TYPE_JAVA);

        List<Path> files = List.of(generatedClass);
        List<Class<?>> dependencies = List.of(token);
        compile(files, dependencies, StandardCharsets.UTF_8);

        Path classFile = resolveFile(packageName, currentPath, classImplName, FILE_TYPE_CLASS);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        writeToJar(packageName, classImplName, jarFile, classFile, manifest);
    }


    /**
     * Main method used to run this {@link Implementor} with custom arguments.<br>
     * Possible arguments:
     * <ul>
     *     <li>{@code "{full-interface-or-class-name}"} – the fully qualified name of the interface or class to implement.</li>
     *     <li>{@code -jar {full-interface-or-class-name} {file}.jar} – specifies that the provided
     *         interface or class should be implemented and packaged into the specified {@code .jar} file.</li>
     * </ul>
     *
     * @param args command-line arguments:
     *             <ul>
     *                 <li>If one argument is provided, it should be the fully qualified name of the interface or class.</li>
     *                 <li>If three arguments are provided, the first must be {@code "-jar"},
     *                 followed by the class/interface name and the output {@code .jar} file path.</li>
     *             </ul>
     */
    public static void main(String[] args) {
        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Args should not be null.");
            return;
        }

        if (args.length == 1) {
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
        } else if (args.length == 3) {
            if (!args[0].trim().equals("-jar")) {
                System.err.println("First argument should be '-jar' option.");
                return;
            }

            String fullInterfaceName = args[1];
            String fullJarFileName = args[2];

            try {
                Class<?> token = Class.forName(fullInterfaceName);
                Path jarFile = Path.of(fullJarFileName);
                new Implementor().implementJar(token, jarFile);
            } catch (ClassNotFoundException e) {
                System.err.printf("No definition for the class with the specified name '%s' could be found",
                        fullInterfaceName);
            } catch (InvalidPathException e) {
                System.err.printf("Provided path '%s' is invalid", fullJarFileName);
            } catch (ImplerException e) {
                System.err.printf("Exception thrown while implementing interface '%s':%n%s",
                        fullInterfaceName,
                        e.getMessage());
            }
        } else {
            System.err.printf("In command line should be 1 or 3 arguments:" +
                    " '[full-interface-or-class-name]' or '-jar [class-or-interface-name] [file].jar'." +
                    "Given: %s", Arrays.toString(args));
        }
    }
}
