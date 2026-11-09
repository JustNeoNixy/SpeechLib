package dev.speechlib.internal;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssetDownloadsTest {
    @TempDir Path directory;
    @Test void concurrentCallersShareVerifiedAssetsAndDamagedDownloadsNeverReplaceThem()throws Exception {
        byte[] payload="verified model fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        var requests=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/model",exchange->{requests.incrementAndGet();exchange.sendResponseHeaders(200,payload.length);try(var body=exchange.getResponseBody()){body.write(payload);}});
        server.start();
        try(var workers=Executors.newFixedThreadPool(2)) {
            URI url=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/model");Path target=directory.resolve("model.bin");
            Callable<Path> fetch=()->AssetDownloads.ensure(target,url,hash,payload.length,null,text->{},"fixture");
            var first=workers.submit(fetch);var second=workers.submit(fetch);
            assertEquals(target,first.get(10,TimeUnit.SECONDS));assertEquals(target,second.get(10,TimeUnit.SECONDS));
            assertEquals(1,requests.get());assertArrayEquals(payload,Files.readAllBytes(target));
            Path bad=directory.resolve("bad-source.bin");Files.writeString(bad,"incorrect payload");
            assertThrows(java.io.IOException.class,()->AssetDownloads.ensure(target,url,hash,payload.length+1,bad,text->{},"fixture"));
            assertArrayEquals(payload,Files.readAllBytes(target));
            try(var files=Files.list(directory)){assertEquals(0,files.filter(p->p.toString().endsWith(".part")).count());}
        }finally{server.stop(0);}
    }
    @Test void legacyModelReuseRequiresTheOriginalChecksumAndPreservesItsFile()throws Exception {
        Path original=directory.resolve(".sayitem/speech/gpu-models/model.bin");Files.createDirectories(original.getParent());Files.writeString(original,"legacy model fixture");
        Path target=directory.resolve(".speechlib/gpu-models/model.bin");String hash=BundledFiles.sha256(original);
        AssetDownloads.ensure(target,URI.create("http://127.0.0.1:1/unreachable"),hash,Files.size(original),null,text->{},"model");
        assertEquals(Files.readString(original),Files.readString(target));assertTrue(Files.exists(original));
    }
}
