package com.example.shortsgesturecontrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Paint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Native production-view rain preview. Uses offline fixtures, never real saves/accounts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w393dp-h851dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RainRenderTest {
    static final File OUTPUT=new File(System.getProperty("mochi.rain.output", "/workspace/artifacts/rain-review-20261013"));
    void png(Bitmap b,File file)throws Exception {
        file.getParentFile().mkdirs();
        try(FileOutputStream out=new FileOutputStream(file)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    RectF scene(Object view)throws Exception {
        Method m=view.getClass().getDeclaredMethod("playgroundScene");m.setAccessible(true);return (RectF)m.invoke(view);
    }
    void rainWeather(Object view,boolean night)throws Exception {
        Class<?> type=Class.forName("com.example.shortsgesturecontrol.PetGameView$WeatherState");
        Constructor<?> c=type.getDeclaredConstructor(boolean.class,float.class,boolean.class,float.class,int.class,int.class,int.class);
        c.setAccessible(true);
        PlayDateRenderTest.set(view,"weather",c.newInstance(true,1f,true,1f,61,night?1200:360,night?1320:1080));
    }
    void render(String version)throws Exception {
        PlayDateRenderTest helper=new PlayDateRenderTest();Context context=helper.init();
        Object pet=helper.view(context,"DRAGON","BABY",null,null);
        Object egg=helper.view(context,"DRAGON","EGG",null,null);
        rainWeather(pet,false);rainWeather(egg,true);
        File dir=new File(OUTPUT,version);
        for(int frame=0;frame<180;frame++) {
            long now=10000+Math.round(frame*1000.0/30);
            Bitmap b=helper.bitmap(pet);helper.full(pet,new Canvas(b),now);
            if(frame==30)png(b,new File(dir,"day-full.png"));
            RectF rect=scene(pet);
            Bitmap crop=Bitmap.createBitmap(b,Math.round(rect.left),Math.round(rect.top),Math.round(rect.width()),Math.round(rect.height()));
            png(crop,new File(dir,String.format("day-%03d.png",frame)));crop.recycle();b.recycle();
            b=helper.bitmap(egg);helper.full(egg,new Canvas(b),now);
            if(frame==30)png(b,new File(dir,"night-full.png"));
            rect=scene(egg);
            crop=Bitmap.createBitmap(b,Math.round(rect.left),Math.round(rect.top),Math.round(rect.width()),Math.round(rect.height()));
            png(crop,new File(dir,String.format("night-%03d.png",frame)));crop.recycle();b.recycle();
        }
        System.out.println("PASS_NATIVE "+version+": 180 production scene frames per day/live and night/egg perspective");
    }
    @Test public void previewBaseline()throws Exception {
        assertThrows("Capture the published v128 renderer before copying the rain candidate",
            NoSuchFieldException.class, ()->Class.forName("com.example.shortsgesturecontrol.PetGameView").getDeclaredField("rainEffect"));
        render("baseline");
    }
    @Test public void previewCandidate()throws Exception {render("candidate-clouds");}

    @Test public void previewCloudStills()throws Exception {
        PlayDateRenderTest helper=new PlayDateRenderTest();Context context=helper.init();
        File dir=new File(OUTPUT,"candidate-puffy-clouds");
        for(boolean night:new boolean[]{false,true}) {
            Object v=helper.view(context,"DRAGON",night?"EGG":"BABY",null,null);
            rainWeather(v,night);
            Bitmap full=helper.bitmap(v);helper.full(v,new Canvas(full),11000);
            String prefix=night?"night":"day";
            png(full,new File(dir,prefix+"-full.png"));
            RectF rect=scene(v);
            Bitmap crop=Bitmap.createBitmap(full,Math.round(rect.left),Math.round(rect.top),Math.round(rect.width()),Math.round(rect.height()));
            png(crop,new File(dir,prefix+"-scene.png"));crop.recycle();full.recycle();
        }
        System.out.println("PASS_NATIVE puffy cloud stills, production day/live and night/egg views; no video frames");
    }

    static class RecordingCanvas extends Canvas {
        final List<float[]> lines=new ArrayList<>();
        @Override public void drawLine(float x1,float y1,float x2,float y2,Paint paint) {
            lines.add(new float[]{x1,y1,x2,y2,paint.getStrokeWidth(),paint.getAlpha()});
        }
    }
    @Test public void independentDropsAndSceneClipping()throws Exception {
        PlayDateRenderTest helper=new PlayDateRenderTest();Context context=helper.init();
        float d=context.getResources().getDisplayMetrics().density;
        RainEffect effect=new RainEffect(d);RectF rect=new RectF(18*d,77*d,375*d,415*d);
        int streaks=0,splashes=0;
        for(int frame=0;frame<360;frame++) {
            RecordingCanvas canvas=new RecordingCanvas();effect.draw(canvas,rect,10000+frame*16,1);
            assertTrue("Unbounded drawing workload",canvas.lines.size()<=168);
            for(int i=0;i<canvas.lines.size();i+=2) {
                float[] a=canvas.lines.get(i),b=canvas.lines.get(i+1);
                for(float f:a)assertTrue("Invalid rain coordinate",Float.isFinite(f));
                if(a[2]==b[2]&&a[3]==b[3]) {
                    assertTrue("Streak points against its fall direction",a[0]>a[2]&&a[1]<a[3]);
                    assertEquals(.12f,(a[0]-a[2])/(a[3]-a[1]),.001f);
                    assertTrue("Long stripe instead of a drop",a[3]-a[1]<=11.01*d);
                    assertTrue("Not tapered",a[5]<b[5]);streaks++;
                } else splashes++;
            }
        }
        assertTrue("Rain missing",streaks>10000);assertTrue("Ground splashes missing",splashes>100);
        RecordingCanvas first=new RecordingCanvas(),repeat=new RecordingCanvas();
        effect.draw(first,rect,12345,0);
        // Intermediate frames and frame-rate changes must not alter the same timestamp.
        effect.draw(new RecordingCanvas(),rect,16000,1);effect.draw(repeat,rect,12345,0);
        assertEquals(first.lines.size(),repeat.lines.size());
        for(int i=0;i<first.lines.size();i++)assertArrayEquals(first.lines.get(i),repeat.lines.get(i),0);
        effect.draw(new RecordingCanvas(),rect,Long.MAX_VALUE,1);
        Object wet=helper.view(context,"DRAGON","BABY",null,null);
        Object dry=helper.view(context,"DRAGON","BABY",null,null);
        rainWeather(wet,false);
        Bitmap rain=helper.bitmap(wet),clear=helper.bitmap(dry);
        PlayDateRenderTest.draw(wet,"drawPlayground",new Canvas(rain),10000);
        PlayDateRenderTest.draw(dry,"drawPlayground",new Canvas(clear),10000);
        RectF scene=scene(wet);int changed=0;
        RectF antialiasBounds=new RectF(scene);antialiasBounds.inset(-1,-1);
        for(int y=0;y<rain.getHeight();y++)for(int x=0;x<rain.getWidth();x++) {
            // A fractional scene edge may cover part of an edge pixel whose
            // center is outside. Permit only the native 1px antialias fringe.
            if(!antialiasBounds.contains(x+.5f,y+.5f))assertEquals("Rain escaped the scenery clip at "+x+","+y,clear.getPixel(x,y),rain.getPixel(x,y));
            else if(clear.getPixel(x,y)!=rain.getPixel(x,y))changed++;
        }
        assertTrue("Wet scene was not rendered",changed>100);
        rain.recycle();clear.recycle();
        System.out.println("PASS_NATIVE bounded/tapered strokes, direction, splashes, deterministic elapsed motion and scene clipping");
    }

    @Test public void overcastCoverAndDayIntensity()throws Exception {
        PlayDateRenderTest helper=new PlayDateRenderTest();Context context=helper.init();
        float d=context.getResources().getDisplayMetrics().density;
        RectF rect=new RectF(18*d,77*d,375*d,415*d);
        RainEffect effect=new RainEffect(d);
        RecordingCanvas day=new RecordingCanvas(),night=new RecordingCanvas();
        effect.draw(day,rect,12345,1);effect.draw(night,rect,12345,0);
        assertTrue("Day rain should be a little denser",day.lines.size()>night.lines.size());
        assertTrue("Night drawing workload changed",night.lines.size()<=144);
        for(int i=0;i<night.lines.size();i++) {
            float[] a=day.lines.get(i),b=night.lines.get(i);
            for(int j=0;j<5;j++)assertEquals("Existing drop motion changed between day and night",b[j],a[j],0);
            assertTrue("Day drops should be a little more visible",a[5]>=b[5]);
        }
        RainCloudCover cover=new RainCloudCover(d);
        for(float daylight:new float[]{0,1})for(long time:new long[]{0,10000,120000,9000000}) {
            Bitmap b=Bitmap.createBitmap(Math.round(393*d),Math.round(851*d),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(b);canvas.save();canvas.clipRect(rect);cover.draw(canvas,rect,time,daylight);canvas.restore();
            // The ceiling must cover the width, not drift off-screen as the old small puffs did.
            for(int y=Math.round(rect.top+10*d);y<rect.top+45*d;y++)
                for(int x=Math.round(rect.left+2*d);x<rect.right-2*d;x++)
                    assertEquals("Gap in thick cloud ceiling",255,b.getPixel(x,y)>>>24);
            b.recycle();
        }
        for(boolean isNight:new boolean[]{false,true}) {
            Object wet=helper.view(context,"DRAGON","BABY",null,null),dry=helper.view(context,"DRAGON","BABY",null,null);
            Class<?> type=Class.forName("com.example.shortsgesturecontrol.PetGameView$WeatherState");
            Constructor<?> c=type.getDeclaredConstructor(boolean.class,float.class,boolean.class,float.class,int.class,int.class,int.class);
            c.setAccessible(true);
            // Even zero reported cloud cover must not produce a clear raining sky.
            PlayDateRenderTest.set(wet,"weather",c.newInstance(true,0f,true,0f,61,isNight?1200:360,isNight?1320:1080));
            PlayDateRenderTest.set(dry,"weather",c.newInstance(true,0f,false,0f,0,isNight?1200:360,isNight?1320:1080));
            Bitmap a=helper.bitmap(wet),b=helper.bitmap(dry);
            PlayDateRenderTest.draw(wet,"drawPlayground",new Canvas(a),10000);
            PlayDateRenderTest.draw(dry,"drawPlayground",new Canvas(b),10000);
            RectF s=scene(wet);double rainLight=0,clearLight=0;int n=0;
            for(int y=Math.round(s.top+55*d);y<s.top+100*d;y++)for(int x=Math.round(s.left+8*d);x<s.right-8*d;x++) {
                int r=a.getPixel(x,y),cl=b.getPixel(x,y);
                rainLight+=(android.graphics.Color.red(r)+android.graphics.Color.green(r)+android.graphics.Color.blue(r))/3.0;
                clearLight+=(android.graphics.Color.red(cl)+android.graphics.Color.green(cl)+android.graphics.Color.blue(cl))/3.0;n++;
            }
            if(!isNight)assertTrue("Day rain remains bright and sunny",rainLight<clearLight*.82);
            png(b,new File(OUTPUT,"candidate-clouds/clear-"+(isNight?"night":"day")+".png"));
            a.recycle();b.recycle();
        }
        System.out.println("PASS_NATIVE modest day-only intensity, unchanged night drop paths, full cloud ceiling at all phases, darker day and clear-weather previews");
    }
}
