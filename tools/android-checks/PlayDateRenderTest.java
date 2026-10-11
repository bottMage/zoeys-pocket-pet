package com.example.shortsgesturecontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.*;
import java.lang.reflect.*;
import java.time.LocalTime;
import java.util.*;
import static org.junit.Assert.*;

/** Actual production Canvas paths, offline Firebase fixtures, original APK artwork.
 * Copy into an isolated Robolectric test project; never connect real accounts.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="w393dp-h851dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayDateRenderTest {
    static final File OUTPUT=new File("/workspace/artifacts/cloud-bubbles-v128");
    static Object get(Object o,String name)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    static void set(Object o,String name,Object value)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    static Object constant(String type,String name)throws Exception {
        Class<?> c=Class.forName("com.example.shortsgesturecontrol.PetGameView$"+type);
        return Arrays.stream(c.getEnumConstants()).filter(v->v.toString().equals(name)).findFirst().orElseThrow();
    }
    static void draw(Object v,String method,Canvas canvas,long now)throws Exception {
        Method m;
        try {m=v.getClass().getDeclaredMethod(method,Canvas.class,long.class);m.setAccessible(true);m.invoke(v,canvas,now);}
        catch(NoSuchMethodException e){m=v.getClass().getDeclaredMethod(method,Canvas.class);m.setAccessible(true);m.invoke(v,canvas);}
    }
    static float number(Object v,String name)throws Exception{return ((Number)get(v,name)).floatValue();}
    Context init() {
        Context context=RuntimeEnvironment.getApplication();
        for(FirebaseApp app:FirebaseApp.getApps(context))app.delete();
        FirebaseApp app=FirebaseApp.initializeApp(context,new FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:playdatefixture")
            .setApiKey("AIzaSyPreviewOnlyNotARealKey00000000000").setProjectId("preview-offline").build());
        app.setDataCollectionDefaultEnabled(false);
        FirebaseAuth.getInstance().useEmulator("127.0.0.1",59999);
        FirebaseFirestore.getInstance().useEmulator("127.0.0.1",59999);
        FirebaseFirestore.getInstance().disableNetwork();
        int offset=12-LocalTime.now(java.time.ZoneOffset.UTC).getHour();
        TimeZone.setDefault(TimeZone.getTimeZone(String.format("GMT%+d",offset)));
        OUTPUT.mkdirs();return context;
    }
    Object view(Context context,String kind,String stage,String peerKind,String peerStage)throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("zoey_pet",Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        prefs.edit().clear().putBoolean("created",true).putBoolean("hatched",!stage.equals("EGG"))
            .putString("kind",kind).putString("name",stage.equals("EGG")?"Drago":"Bunny")
            .putString("player_name",stage.equals("EGG")?"Jacques":"Zoe")
            .putInt("generation",stage.equals("ADULT")?2:stage.equals("YOUNG")?1:0)
            .putLong("last_update",now).putLong("saved_at",now).putLong("age_millis",3600000L)
            .putFloat("hunger",90).putFloat("joy",90).putFloat("energy",90).putFloat("clean",90).commit();
        Class<?> type=Class.forName("com.example.shortsgesturecontrol.PetGameView");
        Constructor<?> constructor=type.getDeclaredConstructor(Context.class,kotlin.jvm.functions.Function0.class);
        constructor.setAccessible(true);
        Object v=constructor.newInstance(context,(kotlin.jvm.functions.Function0<kotlin.Unit>)()->kotlin.Unit.INSTANCE);
        float density=context.getResources().getDisplayMetrics().density;
        ((View)v).layout(0,0,Math.round(393*density),Math.round(851*density));
        set(v,"animationStart",0L);set(v,"activityResumed",false);
        set(v,"motionMode",constant("MotionMode","WALK"));set(v,"motionModeUntil",Long.MAX_VALUE);
        set(v,"motionModeStartedAt",-1000L);set(v,"motionLastAt",0L);
        set(v,"motionX",.5f);set(v,"motionDirection",1f);
        if(peerKind!=null) {
            set(v,"playSessionId","offline-fixture");
            set(v,"playFriend",new SocialPlayManager.Friend("fixture-peer","Friend",peerStage.equals("EGG")?"Drago":"Bunny",peerKind,peerStage,true,now,.85f));
            set(v,"friendMotionX",.5f);set(v,"friendMotionDirection",-1f);set(v,"friendMotionLastAt",0L);
        }
        return v;
    }
    Bitmap bitmap(Object v) {
        Bitmap bitmap=Bitmap.createBitmap(((View)v).getWidth(),((View)v).getHeight(),Bitmap.Config.ARGB_8888);
        bitmap.setDensity(RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().densityDpi);return bitmap;
    }
    void full(Object v,Canvas canvas,long now)throws Exception {
        for(String method:new String[]{"drawBackground","drawHeader","drawPlayground","drawScenePets","drawPlayDateSpeechBubbles","drawActionEffects","drawMessage","drawStats","drawActions"})draw(v,method,canvas,now);
    }
    void png(Bitmap b,String name)throws Exception {
        try(FileOutputStream output=new FileOutputStream(new File(OUTPUT,name))) {
            assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,output));
        }
    }
    void sizesAndOpacity(Context context)throws Exception {
        int cases=0;
        for(String kind:new String[]{"CAT","DOG","BUNNY","HAMSTER","DRAGON"})
        for(String stage:new String[]{"BABY","YOUNG","ADULT"}) {
            Object solo=view(context,kind,stage,null,null);
            Object visiting=view(context,"DRAGON","EGG",kind,stage);
            Bitmap owner=bitmap(solo),peer=bitmap(visiting);
            draw(solo,"drawPet",new Canvas(owner),0);
            // Draw the visitor before any local rendering: size cannot depend on stale local fields.
            draw(visiting,"drawFriendPet",new Canvas(peer),0);
            assertEquals(kind+" "+stage+" must match its own normal size",number(solo,"petRenderWidth"),number(visiting,"friendRenderWidth"),.01f);
            int opaque=0;
            // Original cels contain alpha-250..254 interior pixels. Preserve
            // those original pixels, rather than demanding a 1-bit alpha mask.
            for(int y=0;y<peer.getHeight()/2;y++)for(int x=0;x<peer.getWidth();x++)if((peer.getPixel(x,y)>>>24)>=250)opaque++;
            assertTrue("Visitor inherited shadow alpha: "+kind+" "+stage+", "+opaque+" opaque pixels",opaque>1000);
            assertEquals(255,((android.graphics.Paint)get(visiting,"friendBitmapPaint")).getAlpha());
            assertEquals("Live pet baseline must be independent of egg",number(solo,"petGroundY"),number(visiting,"friendGroundY"),.01f);
            owner.recycle();peer.recycle();cases++;
        }
        System.out.println("PASS_NATIVE size/stage/opacity: "+cases+" species-stage cases at density "+context.getResources().getDisplayMetrics().density);
    }
    @Test public void sizesAndOpacityHdpi()throws Exception {sizesAndOpacity(init());}
    @Test @Config(qualifiers="w393dp-h851dp-xxhdpi")
    public void sizesAndOpacityHighDensity()throws Exception {sizesAndOpacity(init());}
    @Test public void crossingDepthAndCache()throws Exception {
        Context context=init();float density=context.getResources().getDisplayMetrics().density;
        for(String[] pair:new String[][]{{"DRAGON","EGG","BUNNY","BABY"},{"BUNNY","BABY","DRAGON","EGG"},{"BUNNY","BABY","DRAGON","BABY"}}) {
            Object v=view(context,pair[0],pair[1],pair[2],pair[3]);Bitmap b=bitmap(v);Canvas canvas=new Canvas(b);
            for(int frame=0;frame<240;frame++) {
                b.eraseColor(0);draw(v,"drawScenePets",canvas,frame*16L);
                if(!pair[1].equals("EGG"))assertEquals("Local pet reverses at peer",1f,number(v,"motionDirection"),0);
                if(!pair[3].equals("EGG"))assertEquals("Visitor reverses at peer",-1f,number(v,"friendMotionDirection"),0);
            }
            if(!pair[1].equals("EGG"))assertTrue("Owner is stuck in place",number(v,"petCenterX")>b.getWidth()*.5f+20*density);
            if(!pair[3].equals("EGG"))assertTrue("Visitor is stuck in place",number(v,"friendRenderCenterX")<b.getWidth()*.5f-20*density);
            if(!pair[1].equals("EGG")&&!pair[3].equals("EGG")) {
                Map<?,?> cache=(Map<?,?>)get(v,"walkFrameCache");assertEquals(23,cache.size());
                Set<?> warmed=(Set<?>)get(v,"warmedKinds");assertEquals(2,warmed.size());
                Object[] cels=cache.values().toArray();
                draw(v,"drawScenePets",canvas,4000);
                assertTrue("Cached cels were decoded again",cache.values().containsAll(Arrays.asList(cels)));
            }
            b.recycle();
        }
        for(boolean localFront:new boolean[]{true,false}) {
            Object v=view(context,"BUNNY","BABY","DRAGON","BABY");
            set(v,"localPlayPetInFront",localFront);
            Bitmap composite=bitmap(v),local=bitmap(v),remote=bitmap(v);
            draw(v,"drawScenePets",new Canvas(composite),0);
            draw(v,"drawPet",new Canvas(local),0);draw(v,"drawFriendPet",new Canvas(remote),0);
            assertEquals("Wrong depth lane",localFront?10*density:-10*density,number(v,"petGroundY")-number(v,"friendGroundY"),.01f);
            Bitmap front=localFront?local:remote,back=localFront?remote:local;int tested=0;
            for(int y=0;y<composite.getHeight()/2;y++)for(int x=0;x<composite.getWidth();x++) {
                int f=front.getPixel(x,y),b=back.getPixel(x,y),actual=composite.getPixel(x,y);
                if((f>>>24)>=250&&(b>>>24)>=250) {
                    double fa=(f>>>24)/255.0,ba=(b>>>24)/255.0,alpha=fa+ba*(1-fa);
                    for(int shift:new int[]{0,8,16}) {
                        int expected=(int)Math.round((((f>>>shift)&255)*fa+((b>>>shift)&255)*ba*(1-fa))/alpha);
                        assertTrue("Wrong front/back pixel composition",Math.abs(((actual>>>shift)&255)-expected)<=2);
                    }
                    assertTrue("Crossing is unexpectedly transparent",(actual>>>24)>=250);tested++;
                }
            }
            png(composite,"crossing-"+(localFront?"local-front":"remote-front")+".png");
            assertTrue("No opaque crossing was tested: "+tested,tested>500);
            composite.recycle();local.recycle();remote.recycle();
        }
        System.out.println("PASS_NATIVE mixed/live traversal, zero peer-induced turns, two-species cache, both depth orders and source-alpha-preserving pixel composition");
    }
    @Test public void previewBothPerspectivesAndEggRegression()throws Exception {
        Context context=init();
        Object egg=view(context,"DRAGON","EGG","BUNNY","BABY");
        Object bunny=view(context,"BUNNY","BABY","DRAGON","EGG");
        set(egg,"friendMotionX",.72f);set(bunny,"motionX",.32f);
        set(egg,"localPlayAction","hello");set(egg,"remotePlayAction","wave");
        set(bunny,"localPlayAction","wave");set(bunny,"remotePlayAction","hello");
        for(Object v:new Object[]{egg,bunny}){set(v,"playActionUntil",8000L);set(v,"remotePlayActionUntil",8000L);}
        for(int frame=0;frame<120;frame++) {
            for(int side=0;side<2;side++) {
                Object v=side==0?egg:bunny;Bitmap b=bitmap(v);full(v,new Canvas(b),Math.round(frame*1000.0/30));
                png(b,String.format("%s-%03d.png",side==0?"egg-owner":"bunny-owner",frame));b.recycle();
            }
        }
        Object eggs=view(context,"DRAGON","EGG","BUNNY","EGG");
        Bitmap b=bitmap(eggs);full(eggs,new Canvas(b),0);png(b,"both-eggs.png");
        assertEquals(number(eggs,"petRenderWidth"),number(eggs,"friendRenderWidth"),.01f);
        assertEquals(number(eggs,"petEggScale"),number(eggs,"friendEggScale"),.001f);
        assertEquals(b.getWidth()*.32f,number(eggs,"petCenterX"),.01f);
        assertEquals(b.getWidth()*.68f,number(eggs,"friendRenderCenterX"),.01f);
        b.recycle();System.out.println("PASS_NATIVE 120 frames per mixed perspective, speech bubbles, full-size eggs with unchanged positions");
    }

    @Test public void themedCloudSpeechAndExpiry()throws Exception {
        Context context=init();float d=context.getResources().getDisplayMetrics().density;
        CloudSpeechBubble cloud=new CloudSpeechBubble(d);
        for(String label:new String[]{"HI!","WAVE","DANCE!","♥"}) {
            for(float x:new float[]{0,196.5f*d,393*d}) {
                Bitmap b=Bitmap.createBitmap(Math.round(393*d),Math.round(100*d),Bitmap.Config.ARGB_8888);
                cloud.draw(new Canvas(b),label,x,60*d,b.getWidth(),android.graphics.Color.rgb(68,43,90));
                int filled=0,dark=0;
                for(int y=0;y<b.getHeight();y++)for(int px=0;px<b.getWidth();px++) {
                    int color=b.getPixel(px,y);
                    if((color>>>24)>0) {
                        assertTrue("Cloud or shadow clipped at scene edge",px>=5*d&&px<b.getWidth()-5*d);
                        assertTrue("Cloud escaped speech bounds",y>=22*d&&y<72*d);
                        filled++;
                        if((color>>>24)>240&&android.graphics.Color.red(color)<120)dark++;
                    }
                }
                assertTrue("Cloud missing",filled>1000);
                assertTrue("Readable label and outline missing",dark>100);
                b.recycle();
            }
        }
        for(String[] pair:new String[][]{{"DRAGON","EGG","BUNNY","EGG"},{"BUNNY","BABY","DRAGON","BABY"}}) {
            Object v=view(context,pair[0],pair[1],pair[2],pair[3]);
            set(v,"localPlayAction","dance");set(v,"remotePlayAction","hello");
            set(v,"playActionUntil",1000L);set(v,"remotePlayActionUntil",1000L);
            Bitmap b=bitmap(v);full(v,new Canvas(b),0);png(b,pair[1].equals("EGG")?"cloud-eggs.png":"cloud-live-pets.png");
            b.eraseColor(0);draw(v,"drawPlayDateSpeechBubbles",new Canvas(b),1000);
            for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)assertEquals("Expired bubble persists",0,b.getPixel(x,y));
            // Both bubbles independently follow their current rendered head coordinates.
            set(v,"petCenterX",100*d);set(v,"friendRenderCenterX",290*d);
            draw(v,"drawPlayDateSpeechBubbles",new Canvas(b),0);
            int left=0,right=0;
            for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if((b.getPixel(x,y)>>>24)>0) {
                if(x<196*d)left++;else right++;
            }
            assertTrue("Owner bubble did not follow",left>1000);
            assertTrue("Visitor bubble did not follow",right>1000);
            b.recycle();
        }
        System.out.println("PASS_NATIVE cloud labels, scene-edge bounds, both eggs/live pets, independent expiry and moving anchors");
    }
}
