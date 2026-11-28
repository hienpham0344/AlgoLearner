package com.algolearner.platform.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Samples the local Docker daemon's cgroup v2 high-water mark, when visible to the worker. */
final class ContainerMemoryMeter {
    private final Path cidFile;
    private List<Path> candidates;
    private Long peakKb;

    ContainerMemoryMeter(Path cidFile) { this.cidFile=cidFile; }

    void sample() {
        try {
            if(candidates==null) {
                if(!Files.isRegularFile(cidFile)) return;
                String id=Files.readString(cidFile).strip();
                if(!id.matches("[0-9a-f]{64}")) return;
                Path root=Path.of("/sys/fs/cgroup");
                candidates=List.of(root.resolve("system.slice/docker-"+id+".scope/memory.peak"),
                        root.resolve("docker/"+id+"/memory.peak"));
            }
            for(Path path:candidates) {
                if(!Files.isRegularFile(path)) continue;
                long bytes=Long.parseLong(Files.readString(path).strip());
                if(bytes>0) peakKb=Math.max(peakKb==null?0L:peakKb,(bytes+1023)/1024);
            }
        } catch(IOException | NumberFormatException ignored) {
            // Very short lived containers may exit before the first sample.
        }
    }

    Long peakKb() { return peakKb; }
}
