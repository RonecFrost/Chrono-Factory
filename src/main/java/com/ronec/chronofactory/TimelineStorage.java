package com.ronec.chronofactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.ItemStack;
import com.mojang.logging.LogUtils;

/** Immutable, segmented recording payloads. Item stacks and active-job saves
 * contain only lightweight references; playback hydrates once and runs a
 * continuous cursor across the reconstructed streams. */
final class TimelineStorage{
    static final int MAX_PART=1024*1024, MAX_BYTES=16*1024*1024, MAX_GUI=32768, MAX_TICKS=72000, MAX_PARTS=256;
    static final String[] LISTS={"actions","gui","flights"};
    static final String[] ARRAYS={"raw","motion"};
    private static final Map<MinecraftServer,LinkedHashMap<UUID,CompoundTag>> CACHE=new WeakHashMap<>();
    static LinkedHashMap<UUID,CompoundTag> cache(MinecraftServer server){return CACHE.computeIfAbsent(server,k->new LinkedHashMap<>(8,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<UUID,CompoundTag> e){return size()>3;}});}
    static Path root(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("chronofactory_timelines");}
    static int count(CompoundTag tl){return tl.hasUUID("storage")?tl.getInt("actionCount"):ChronoFactory.timeline(tl).size();}
    static CompoundTag reference(CompoundTag tl){
        if(!tl.hasUUID("storage"))return tl.copy();CompoundTag ref=new CompoundTag();
        for(String k:tl.getAllKeys())if(!Arrays.asList(LISTS).contains(k)&&!Arrays.asList(ARRAYS).contains(k))ref.put(k,tl.get(k).copy());return ref;
    }
    static CompoundTag attach(CompoundTag ref,CompoundTag payload){
        CompoundTag result=reference(ref);
        for(String k:LISTS)result.put(k,payload.getList(k,Tag.TAG_COMPOUND));
        for(String k:ARRAYS)result.put(k,payload.get(k)==null?new IntArrayTag(new int[0]):payload.get(k));
        return result;
    }
    static String limit(ChronoFactory.Recording r){
        if(!r.limitReason.isEmpty())return r.limitReason;
        if(r.actions.size()>=ChronoFactory.MAX_ACTIONS-16)return "Action limit reached";
        if(r.raw.size()/3>=MAX_TICKS)return "60-minute recording limit reached";
        if(r.gui.size()>=MAX_GUI-16)return "GUI action limit reached";
        if(r.tick%10==0){long n=(long)r.actions.sizeInBytes()+r.gui.sizeInBytes()+r.flights.sizeInBytes()+4L*(r.raw.size()+r.motion.size());if(n>=MAX_BYTES)return "Recording data size limit reached";}
        return "";
    }
    static void part(Path dir,int index,CompoundTag data)throws IOException{
        if(index>=MAX_PARTS||data.sizeInBytes()>MAX_PART*2)throw new IOException("Recording segment too large");
        Path path=dir.resolve(String.format(Locale.ROOT,"%04d.nbt",index));NbtIo.writeCompressed(data,path);
    }
    static CompoundTag store(MinecraftServer server,CompoundTag tl){
        if(tl.hasUUID("storage"))return reference(tl);
        UUID id=UUID.randomUUID();Path root=root(server),tmp=root.resolve(id+".tmp"),dest=root.resolve(id.toString());
        int index=0;
        try{
            Files.createDirectories(root);Files.createDirectory(tmp);
            for(String k:LISTS){
                ListTag pending=new ListTag();long bytes=0;
                for(Tag row:tl.getList(k,Tag.TAG_COMPOUND)){
                    int size=row.sizeInBytes();if(size>MAX_PART)throw new IOException("Single "+k+" entry too large");
                    if(!pending.isEmpty()&&bytes+size>MAX_PART){CompoundTag chunk=new CompoundTag();chunk.putString("stream",k);chunk.put("list",pending);part(tmp,index++,chunk);pending=new ListTag();bytes=0;}
                    pending.add(row);bytes+=size;
                }
                if(!pending.isEmpty()){CompoundTag chunk=new CompoundTag();chunk.putString("stream",k);chunk.put("list",pending);part(tmp,index++,chunk);}
            }
            for(String k:ARRAYS){int[] data=tl.getIntArray(k);for(int at=0;at<data.length;at+=18000){CompoundTag chunk=new CompoundTag();chunk.putString("stream",k);chunk.putIntArray("ints",Arrays.copyOfRange(data,at,Math.min(data.length,at+18000)));part(tmp,index++,chunk);}}
            CompoundTag ref=new CompoundTag();for(String k:tl.getAllKeys())if(!Arrays.asList(LISTS).contains(k)&&!Arrays.asList(ARRAYS).contains(k))ref.put(k,tl.get(k).copy());
            ref.putUUID("storage",id);ref.putInt("parts",index);ref.putInt("actionCount",ChronoFactory.timeline(tl).size());ref.putInt("rawTicks",tl.getIntArray("raw").length/3);
            NbtIo.writeCompressed(ref,tmp.resolve("manifest.nbt"));
            try{Files.move(tmp,dest,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,dest);}
            cache(server).put(id,attach(ref,tl));return ref;
        }catch(Exception e){LogUtils.getLogger().error("Chrono Factory failed to store timeline "+id,e);return null;}
    }
    static CompoundTag load(MinecraftServer server,CompoundTag tl){
        if(!tl.hasUUID("storage")||tl.contains("actions"))return tl;
        UUID id=tl.getUUID("storage");CompoundTag found=cache(server).get(id);if(found!=null)return attach(tl,found);
        try{
            int parts=tl.getInt("parts");if(parts<1||parts>MAX_PARTS)throw new IOException("Invalid timeline part count");
            Path dir=root(server).resolve(id.toString());CompoundTag manifest=NbtIo.readCompressed(dir.resolve("manifest.nbt"),NbtAccounter.create(2L*MAX_PART));
            if(!manifest.getUUID("storage").equals(id)||manifest.getInt("parts")!=parts)throw new IOException("Timeline manifest mismatch");
            CompoundTag payload=new CompoundTag();Map<String,List<Integer>> ints=new HashMap<>();for(String k:LISTS)payload.put(k,new ListTag());for(String k:ARRAYS)ints.put(k,new ArrayList<>());
            long bytes=0;
            for(int i=0;i<parts;i++){
                CompoundTag chunk=NbtIo.readCompressed(dir.resolve(String.format(Locale.ROOT,"%04d.nbt",i)),NbtAccounter.create(2L*MAX_PART));
                bytes+=chunk.sizeInBytes();if(bytes>MAX_BYTES*3L)throw new IOException("Timeline exceeds load limit");
                String k=chunk.getString("stream");
                if(ints.containsKey(k)){for(int n:chunk.getIntArray("ints"))ints.get(k).add(n);}
                else if(Arrays.asList(LISTS).contains(k))payload.getList(k,Tag.TAG_COMPOUND).addAll(chunk.getList("list",Tag.TAG_COMPOUND));
                else throw new IOException("Unknown timeline stream");
            }
            for(String k:ARRAYS)payload.putIntArray(k,ints.get(k).stream().mapToInt(Integer::intValue).toArray());
            if(payload.getList("actions",Tag.TAG_COMPOUND).size()!=tl.getInt("actionCount")||payload.getIntArray("raw").length!=tl.getInt("rawTicks")*3||tl.getBoolean("strict")&&payload.getIntArray("motion").length!=payload.getIntArray("raw").length)throw new IOException("Incomplete timeline");
            found=attach(tl,payload);cache(server).put(id,found);return found;
        }catch(Exception e){LogUtils.getLogger().error("Chrono Factory failed to load timeline "+id,e);return null;}
    }
    static Path checkpointPath(MinecraftServer server,UUID watch){return root(server).resolve("checkpoint_"+watch+".nbt");}
    static void clearCheckpoint(MinecraftServer server,UUID watch){
        try{Files.deleteIfExists(checkpointPath(server,watch));}catch(IOException e){LogUtils.getLogger().warn("Could not clear Chrono checkpoint",e);}
    }
    static void checkpoint(MinecraftServer server,UUID watch,CompoundTag tl){
        CompoundTag ref=store(server,tl);if(ref==null)return;
        Path path=checkpointPath(server,watch),tmp=path.resolveSibling(path.getFileName()+".tmp");
        try{NbtIo.writeCompressed(ref,tmp);try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}
        catch(IOException e){LogUtils.getLogger().warn("Could not write Chrono checkpoint",e);}
    }
    static boolean recover(net.minecraft.server.level.ServerPlayer p,ItemStack s){
        if(!ChronoFactory.watch(s))return false;
        UUID watch=ChronoFactory.id(s);Path path=checkpointPath(p.server,watch);if(!Files.isRegularFile(path))return false;
        try{
            CompoundTag ref=NbtIo.readCompressed(path,NbtAccounter.create(2L*MAX_PART));if(load(p.server,ref)==null)return false;
            CompoundTag tag=ChronoFactory.data(s);if(tag.contains("current"))return false;
            tag.put("current",ref);ChronoFactory.put(s,tag);clearCheckpoint(p.server,watch);return true;
        }catch(Exception e){LogUtils.getLogger().warn("Could not recover Chrono checkpoint",e);return false;}
    }
    static void migrateWatch(MinecraftServer server,ItemStack s){
        if(!ChronoFactory.watch(s))return;
        CompoundTag tag=ChronoFactory.data(s);boolean changed=false;
        if(tag.contains("current")){CompoundTag tl=tag.getCompound("current");if(!tl.hasUUID("storage")&&!ChronoFactory.timeline(tl).isEmpty()){CompoundTag ref=store(server,tl);if(ref!=null){tag.put("current",ref);changed=true;}}}
        ListTag list=tag.getList("saved",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){CompoundTag tl=list.getCompound(i);if(!tl.hasUUID("storage")&&!ChronoFactory.timeline(tl).isEmpty()){CompoundTag ref=store(server,tl);if(ref!=null){list.set(i,ref);changed=true;}}}
        if(changed){tag.put("saved",list);ChronoFactory.put(s,tag);}
    }
}
