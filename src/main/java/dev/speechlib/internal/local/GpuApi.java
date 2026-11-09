package dev.speechlib.internal.local;

import com.google.gson.JsonParser;
import com.sun.jna.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

interface GpuApi extends Library {
    interface Cancelled extends Callback { int invoke(Pointer data); }
    String speechlib_gpu_version();
    String speechlib_gpu_device();
    Pointer speechlib_gpu_load(String model,int gpu,int threads,byte[] error,int capacity);
    void speechlib_gpu_free(Pointer context);
    int speechlib_gpu_transcribe(Pointer context,float[] samples,int length,String language,int beam,
        byte[] text,int capacity,Cancelled cancelled,Pointer data,byte[] error,int errorCapacity);
    final class Loaded {
        private static GpuApi api;
        private static final List<NativeLibrary> libraries=new ArrayList<>();
    }
    static GpuApi load(Path root) throws IOException {
        synchronized(Loaded.class) {
            if(Loaded.api!=null)return Loaded.api;
            var metadata=JsonParser.parseString(Files.readString(root.resolve("source.json"))).getAsJsonObject();
            for(var value:metadata.getAsJsonArray("preload")) Loaded.libraries.add(NativeLibrary.getInstance(root.resolve(value.getAsString()).toString()));
            Loaded.api=Native.load(root.resolve(metadata.get("library").getAsString()).toString(),GpuApi.class,
                Map.of(Library.OPTION_STRING_ENCODING,"UTF-8"));
            return Loaded.api;
        }
    }
}
