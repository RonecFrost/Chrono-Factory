package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
final class LaunchPreview{
    record Pending(UUID watch,UUID timeline,String dimension,BlockPos shift){}
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    static boolean armed(ServerPlayer p,UUID watch){Pending q=PENDING.get(p.getUUID());return q!=null&&q.watch.equals(watch);}
    static void cancel(ServerPlayer p){PENDING.remove(p.getUUID());}
    static CompoundTag timeline(ItemStack s,UUID id){
        CompoundTag t=ChronoFactory.data(s);if(t.contains("current")&&t.getCompound("current").getUUID("id").equals(id))return t.getCompound("current");
        for(Tag x:t.getList("saved",Tag.TAG_COMPOUND)){CompoundTag a=(CompoundTag)x;if(a.getUUID("id").equals(id))return a;}return null;
    }
    static void arm(ServerPlayer p,ItemStack s,int index,boolean offset){
        CompoundTag t=ChronoFactory.data(s);ListTag saved=t.getList("saved",Tag.TAG_COMPOUND);
        CompoundTag tl=index==-1?t.getCompound("current"):index>=0&&index<saved.size()?saved.getCompound(index):new CompoundTag();
        if(!ChronoFactory.validTimeline(tl)||!tl.hasUUID("id")||ChronoFactory.active(p,ChronoFactory.id(s),tl.getUUID("id"))){ChronoFactory.say(p,"Timeline not available for preview.");return;}
        tl=TimelineStorage.load(p.server,tl);if(tl==null){ChronoFactory.say(p,"Timeline files missing or unreadable.");return;}
        String dimension=offset?p.level().dimension().location().toString():tl.getString("dimension");
        if(!dimension.equals(p.level().dimension().location().toString())){ChronoFactory.say(p,"Go to the original dimension or use Start at me.");return;}
        BlockPos origin=BlockPos.containing(ChronoFactory.vec(ChronoFactory.timeline(tl).getCompound(0)));
        BlockPos shift=offset?p.blockPosition().subtract(origin):BlockPos.ZERO;
        PENDING.put(p.getUUID(),new Pending(ChronoFactory.id(s),tl.getUUID("id"),dimension,shift));
        ChronoFactory.say(p,"Route armed. Left-click with this Timekeeper to start; right-click to cancel. You may switch items.");
        render(p,tl,shift);
    }
    static boolean confirm(ServerPlayer p,ItemStack s){
        Pending q=PENDING.get(p.getUUID());if(q==null||!q.watch.equals(ChronoFactory.id(s)))return false;
        CompoundTag tl=timeline(s,q.timeline);if(tl==null){cancel(p);ChronoFactory.say(p,"Preview timeline no longer exists.");return true;}
        if(!q.dimension.equals(p.level().dimension().location().toString())){ChronoFactory.say(p,"Return to the preview dimension.");return true;}
        tl=TimelineStorage.load(p.server,tl);if(tl==null){cancel(p);ChronoFactory.say(p,"Timeline files missing or unreadable.");return true;}
        if(ChronoFactory.manifestAt(p,s,translate(tl,q.shift,q.dimension)))cancel(p);return true;
    }
    static void tick(ServerPlayer p){
        Pending q=PENDING.get(p.getUUID());if(q==null||p.tickCount%10!=0)return;
        ItemStack s=ChronoFactory.find(p,q.watch);if(s.isEmpty()){cancel(p);return;}
        CompoundTag tl=timeline(s,q.timeline);if(tl==null){cancel(p);return;}
        if(q.dimension.equals(p.level().dimension().location().toString())){tl=TimelineStorage.load(p.server,tl);if(tl!=null)render(p,tl,q.shift);else cancel(p);}
    }
    static void triple(CompoundTag a,String x,String y,String z,BlockPos d){if(a.contains(x)&&a.contains(y)&&a.contains(z)){a.putDouble(x,a.getDouble(x)+d.getX());a.putDouble(y,a.getDouble(y)+d.getY());a.putDouble(z,a.getDouble(z)+d.getZ());}}
    static void translateAction(CompoundTag a,BlockPos d){
        triple(a,"x","y","z",d);triple(a,"hx","hy","hz",d);triple(a,"ix","iy","iz",d);
        if(a.contains("pos"))a.putLong("pos",BlockPos.of(a.getLong("pos")).offset(d).asLong());
        for(Tag x:a.getList("useAims",Tag.TAG_COMPOUND))triple((CompoundTag)x,"hx","hy","hz",d);
        if(a.getBoolean("command")&&a.contains("message"))a.putString("message",command(a.getString("message"),d));
    }
    // Translate plain absolute coordinates in self-teleport and common building
    // commands; relative/local coordinates already resolve from the Echo.
    static String command(String raw,BlockPos d){
        String[] a=raw.strip().split("\\s+");if(a.length==0)return raw;
        String root=a[0].replace("minecraft:","");int[] starts;
        if(root.equals("tp")||root.equals("teleport")){if(a.length==4||a.length==6)starts=new int[]{1};else if((a.length==5||a.length==7)&&a[1].equals("@s"))starts=new int[]{2};else return raw;}
        else if(root.equals("setblock")&&a.length>=5)starts=new int[]{1};else if(root.equals("fill")&&a.length>=8)starts=new int[]{1,4};else return raw;
        int[] v={d.getX(),d.getY(),d.getZ()};try{for(int start:starts)for(int k=0;k<3;k++){String c=a[start+k];if(c.startsWith("~")||c.startsWith("^"))continue;double n=Double.parseDouble(c)+v[k];if(!Double.isFinite(n))return raw;a[start+k]=n==Math.rint(n)?Long.toString((long)n):Double.toString(n);}}catch(Exception e){return raw;}
        return String.join(" ",a);
    }
    static CompoundTag translate(CompoundTag original,BlockPos d,String dimension){
        if(d.equals(BlockPos.ZERO)&&dimension.equals(original.getString("dimension")))return original;
        CompoundTag copy=original.copy();copy.remove("storage");copy.remove("parts");copy.putString("dimension",dimension);
        for(Tag x:ChronoFactory.timeline(copy))translateAction((CompoundTag)x,d);
        for(Tag x:copy.getList("gui",Tag.TAG_COMPOUND))translateAction((CompoundTag)x,d);
        for(Tag x:copy.getList("flights",Tag.TAG_COMPOUND)){CompoundTag a=(CompoundTag)x;if(a.contains("impactBlock"))a.putLong("impactBlock",BlockPos.of(a.getLong("impactBlock")).offset(d).asLong());}
        return copy;
    }
    static final DustParticleOptions ROUTE=new DustParticleOptions(new Vector3f(.65f,.3f,1f),.75f),USE=new DustParticleOptions(new Vector3f(.25f,.9f,1f),1f),MISSING=new DustParticleOptions(new Vector3f(1f,.38f,.15f),1f),START=new DustParticleOptions(new Vector3f(.4f,1f,.4f),1.2f);
    static void dot(ServerPlayer p,Vec3 v,DustParticleOptions dust){if(p.position().distanceToSqr(v)<128*128)p.serverLevel().sendParticles(p,dust,false,v.x,v.y,v.z,1,0,0,0,0);}
    static void render(ServerPlayer p,CompoundTag tl,BlockPos d){
        Vec3 offset=new Vec3(d.getX(),d.getY(),d.getZ());ListTag actions=ChronoFactory.timeline(tl);int stride=Math.max(1,(actions.size()+119)/120);
        Vec3 first=ChronoFactory.vec(actions.getCompound(0)).add(offset);for(int k=0;k<8;k++)dot(p,first.add(0,.1+k*.2,0),START);
        Vec3 previous=null;int count=0;
        for(int k=0;k<actions.size();k+=stride){CompoundTag a=actions.getCompound(k);Vec3 v=ChronoFactory.vec(a).add(offset).add(0,.12,0);
            dot(p,v,ROUTE);if(previous!=null&&previous.distanceTo(v)<12){int n=Math.min(4,(int)Math.ceil(previous.distanceTo(v)*2));for(int j=1;j<n;j++)dot(p,previous.lerp(v,j/(double)n),ROUTE);}previous=v;
        }
        for(Tag x:actions){CompoundTag a=(CompoundTag)x;if(!a.contains("pos")||count++>=160)continue;BlockPos pos=BlockPos.of(a.getLong("pos")).offset(d);if(!p.serverLevel().hasChunkAt(pos))continue;
            boolean missing=p.serverLevel().getBlockState(pos).isAir()||a.contains("block")&&!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.serverLevel().getBlockState(pos).getBlock()).toString().equals(a.getString("block"));
            Vec3 center=Vec3.atCenterOf(pos);DustParticleOptions color=missing?MISSING:USE;for(int j=0;j<4;j++){double xx=(j&1)==0?-.48:.48,zz=(j&2)==0?-.48:.48;dot(p,center.add(xx,.52,zz),color);}
        }
    }
}
