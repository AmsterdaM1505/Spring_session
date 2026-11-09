import com.sun.source.util.JavacTask;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import javax.tools.*;

/** Parses the real Java sources with the JDK parser without resolving external libraries. */
public class SourceChecks {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]);
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target") && !p.toString().contains(".work")).toList();
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "17", "-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(sources));
            task.parse();
        }
        long errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).peek(System.err::println).count();
        if (errors != 0) throw new AssertionError("Java syntax errors: " + errors);
        System.out.println("JAVA_PARSE_PASS sources="+sources.size()+" release=17 dependencyResolution=false");
    }
}
