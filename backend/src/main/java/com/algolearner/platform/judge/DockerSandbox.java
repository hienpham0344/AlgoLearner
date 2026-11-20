package com.algolearner.platform.judge;

import static com.algolearner.platform.judge.JudgeGateway.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Worker-only executor. Learner processes never run on the host, including javac. */
final class DockerSandbox {
    private final String javaImage,pythonImage;
    private final Path workRoot;
    private final DockerCommands commands;
    DockerSandbox(String javaImage,String pythonImage,Path workRoot) throws IOException {
        this(javaImage,pythonImage,workRoot,new SystemDockerCommands());
    }
    DockerSandbox(String javaImage,String pythonImage,Path workRoot,DockerCommands commands) throws IOException {
        this.javaImage=requirePinnedImage(javaImage);this.pythonImage=requirePinnedImage(pythonImage);
        this.workRoot=workRoot.toAbsolutePath();Files.createDirectories(this.workRoot);
        this.commands=Objects.requireNonNull(commands);
    }
    static String requirePinnedImage(String image) {
        if(image==null || !image.matches("[a-zA-Z0-9][a-zA-Z0-9._/:\\-]*@sha256:[a-f0-9]{64}"))
            throw new IllegalArgumentException("Judge images must use an immutable sha256 digest");
        return image;
    }
    EvaluationResult evaluate(UUID job,CodeEvaluationRequest r) throws IOException,InterruptedException {
        JudgePolicy.validate(r);
        Path dir=Files.createTempDirectory(workRoot,"judge-");
        String artifact="algo-artifact-"+UUID.randomUUID();
        boolean volumeAttempted=false;
        Throwable failure=null;
        try {
            // Container's unprivileged UID must read sources and write compilation output.
            permissions(dir,"rwxrwxrwx");
            Path source=dir.resolve(r.language()==Language.JAVA?"Main.java":"main.py");
            Files.writeString(source,r.source(),StandardCharsets.UTF_8);permissions(source,"r--r--r--");
            volumeAttempted=true;
            commands.control(List.of("docker","volume","create","--driver","local","--opt","type=tmpfs","--opt","device=tmpfs",
                    "--opt","o=size=32m,uid=65534,gid=65534",artifact));
            if(r.language()==Language.JAVA) {
                var args=command(javaImage,dir,artifact,true);
                args.addAll(List.of("javac","-proc:none","-J-Xmx128m","-J-XX:ActiveProcessorCount=1","-J-XX:+UseSerialGC","-d","/artifact","/source/Main.java"));
                var compiled=commands.execute(args,"",r.limits().compileSeconds()*1000,65536);
                if(compiled.timedOut() || compiled.exit()!=0 || compiled.overflow())
                    return new EvaluationResult(job,r.generation(),Verdict.COMPILE_ERROR,0,r.testCases().size(),List.of());
            }
            int passed=0,index=0;Verdict overall=Verdict.ACCEPTED;var results=new ArrayList<CaseResult>();
            for(var test:r.testCases()) {
                var args=command(r.language()==Language.JAVA?javaImage:pythonImage,dir,artifact,false);
                args.addAll(r.language()==Language.JAVA?List.of("java","-Xmx192m","-XX:ActiveProcessorCount=1","-XX:+UseSerialGC","-cp","/artifact","Main")
                        :List.of("python3","-I","-B","/source/main.py"));
                var execution=commands.execute(args,test.input(),r.limits().runtimeMillis(),r.limits().outputBytes());
                Verdict verdict=execution.timedOut()?Verdict.TIME_LIMIT_EXCEEDED:execution.overflow()?Verdict.OUTPUT_LIMIT_EXCEEDED
                        :execution.outOfMemory()?Verdict.MEMORY_LIMIT_EXCEEDED:execution.exit()!=0?Verdict.RUNTIME_ERROR:JudgePolicy.matches(test.expectedOutput(),execution.stdout())?Verdict.ACCEPTED:Verdict.WRONG_ANSWER;
                if(verdict==Verdict.ACCEPTED)passed++;else if(overall==Verdict.ACCEPTED)overall=verdict;
                var visible=JudgePolicy.visibleCase(index++,test,verdict,execution.stdout(),r.feedback());
                // Persist every verdict for server-side weighted scoring; API projection enforces feedback separately.
                results.add(visible!=null?visible:new CaseResult(index-1,verdict,null,null,null));
            }
            return new EvaluationResult(job,r.generation(),overall,passed,r.testCases().size(),results);
        } catch(IOException|InterruptedException|RuntimeException|Error error) {
            failure=error;
            throw error;
        } finally {
            try {
                if(volumeAttempted)commands.control(List.of("docker","volume","rm","-f",artifact));
            } catch(IOException|InterruptedException cleanup) {
                if(failure!=null)failure.addSuppressed(cleanup);else {failure=cleanup;throw cleanup;}
            } finally {
                // Cleanup of one resource must never prevent cleanup of the source directory.
                try(var paths=Files.walk(dir)) {
                    for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
                } catch(IOException cleanup) {
                    if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;
                }
            }
        }
    }
    static List<String> baseCommand(String image,String name) {
        return new ArrayList<>(List.of("docker","run","--interactive","--name",name,"--pull=never","--network","none",
                "--memory","256m","--memory-swap","256m","--cpus","1.0","--pids-limit","32","--cap-drop=ALL",
                "--read-only","--user","65534:65534","--security-opt","no-new-privileges",
                "--tmpfs","/tmp:rw,noexec,nosuid,nodev,size=32m","--ulimit","fsize=65536:65536",
                "--ulimit","nofile=64:64","--env","HOME=/tmp"));
    }
    private List<String> command(String image,Path source,String artifact,boolean compile) {
        var args=baseCommand(image,"algo-judge-"+UUID.randomUUID());
        args.addAll(List.of("--mount","type=bind,source="+source+",target=/source,readonly",
                "--mount","type=volume,source="+artifact+",target=/artifact"+(compile?"":",readonly"),image));
        return args;
    }
    private static void dockerControl(List<String> args) throws IOException,InterruptedException {
        Process p=new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if(!p.waitFor(10,TimeUnit.SECONDS))throw new IOException("Docker control timed out");
            if(p.exitValue()!=0)throw new IOException("Docker control failed");
        } finally {if(p.isAlive())p.destroyForcibly();}
    }
    record Execution(int exit,String stdout,boolean overflow,boolean timedOut,boolean outOfMemory) { }
    interface DockerCommands {
        void control(List<String> args) throws IOException,InterruptedException;
        Execution execute(List<String> args,String input,int timeoutMillis,int limit) throws IOException,InterruptedException;
    }
    private static final class SystemDockerCommands implements DockerCommands {
        @Override public void control(List<String> args) throws IOException,InterruptedException { dockerControl(args); }
        @Override public Execution execute(List<String> args,String input,int timeoutMillis,int limit) throws IOException,InterruptedException {
            return executeContainer(args,input,timeoutMillis,limit);
        }
    }
    private static Execution executeContainer(List<String> args,String input,int timeoutMillis,int limit) throws IOException,InterruptedException {
        Process process=new ProcessBuilder(args).start();
        String name=args.get(args.indexOf("--name")+1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var overflow=new java.util.concurrent.atomic.AtomicBoolean();
            var stdout=executor.submit(()->drain(process.getInputStream(),limit,overflow));
            var stderr=executor.submit(()->drain(process.getErrorStream(),limit,overflow));
            var writer=executor.submit(()-> {try(var out=process.getOutputStream()){out.write(input.getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){/* An exited learner may close stdin early. */} });
            boolean removed=false;
            Throwable failure=null;
            try {
                boolean finished=process.waitFor(timeoutMillis,TimeUnit.MILLISECONDS);
                if(!finished) {
                    process.destroyForcibly();
                    // Killing the CLI does not kill its container.
                    dockerControl(List.of("docker","rm","-f",name));
                    removed=true;
                }
                String output=stdout.get(5,TimeUnit.SECONDS);stderr.get(5,TimeUnit.SECONDS);writer.get(5,TimeUnit.SECONDS);
                int exit=finished?process.exitValue():-1;
                // Docker infrastructure/daemon failures retry; learner failures remain final verdicts.
                if(finished && exit==125)throw new IOException("Docker infrastructure unavailable");
                boolean oom=finished && exit==137 && outOfMemory(name);
                return new Execution(exit,output,overflow.get(),!finished,oom);
            } catch(ExecutionException|TimeoutException error) {
                var wrapped=new IOException("Bounded container IO failed",error);
                failure=wrapped;
                throw wrapped;
            } catch(IOException|InterruptedException|RuntimeException|Error error) {
                failure=error;
                throw error;
            } finally {
                if(process.isAlive())process.destroyForcibly();
                if(!removed) {
                    try { dockerControl(List.of("docker","rm","-f",name)); }
                    catch(IOException|InterruptedException cleanup) {
                        if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;
                    }
                }
            }
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
    private static boolean outOfMemory(String name) throws IOException,InterruptedException {
        Process inspect=new ProcessBuilder("docker","inspect","--format","{{.State.OOMKilled}}",name)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if(!inspect.waitFor(5,TimeUnit.SECONDS))throw new IOException("Container inspection timed out");
            String result=new String(inspect.getInputStream().readNBytes(16),StandardCharsets.US_ASCII).strip();
            if(inspect.exitValue()!=0 || !(result.equals("true") || result.equals("false")))throw new IOException("Container inspection failed");
            return Boolean.parseBoolean(result);
        } finally {if(inspect.isAlive())inspect.destroyForcibly();}
    }
    private static String drain(InputStream in,int limit,java.util.concurrent.atomic.AtomicBoolean overflow) throws IOException {
        var output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
        while((count=in.read(buffer))!=-1) {
            int remaining=limit-output.size();if(count>remaining)overflow.set(true);
            if(remaining>0)output.write(buffer,0,Math.min(count,remaining));
        }
        return output.toString(StandardCharsets.UTF_8);
    }
    private static void permissions(Path path,String mode) throws IOException {Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString(mode));}
}
