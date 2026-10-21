package com.example.shortsgesturecontrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Native Canvas regression for the sickness-condition placement. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w393dp-h851dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SickConditionRenderTest {
    private final PlayDateRenderTest helper=new PlayDateRenderTest();
    private final File output=new File(System.getProperty("mochi.sick.output", "/workspace/artifacts/sick-condition-review-20261021"));

    private RectF rect(Object view,String name)throws Exception {
        Method method=view.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return new RectF((RectF)method.invoke(view));
    }

    @Test public void sickConditionIsVisibleBelowNewPet()throws Exception {
        Context context=helper.init(); output.mkdirs();
        Object view=helper.view(context,"DRAGON","BABY",null,null);
        Object pet=PlayDateRenderTest.get(view,"pet");
        PetLife life=(PetLife)PlayDateRenderTest.get(pet,"life");
        life.sick=true;
        life.symptom=PetLife.TUMMY_ACHE;

        RectF newPet=rect(view,"newPetRect");
        RectF condition=rect(view,"sickConditionRect");
        assertTrue("Condition must start beneath NEW PET",condition.top>=newPet.bottom);
        assertEquals("Condition stays aligned to NEW PET",newPet.left,condition.left,.01f);
        assertEquals("Condition stays aligned to NEW PET",newPet.right,condition.right,.01f);
        assertTrue("Condition must leave room above metric labels",condition.bottom<newPet.top+74*context.getResources().getDisplayMetrics().density);

        Bitmap bitmap=helper.bitmap(view);
        helper.full(view,new Canvas(bitmap),0);
        int darkText=0,roseFill=0;
        for(int y=(int)condition.top;y<(int)condition.bottom;y++)for(int x=(int)condition.left;x<(int)condition.right;x++) {
            int color=bitmap.getPixel(x,y);
            if(Color.red(color)<190&&Color.green(color)<105&&Color.blue(color)<120)darkText++;
            if(Color.red(color)>235&&Color.green(color)>195&&Color.blue(color)>200)roseFill++;
        }
        assertTrue("Condition label is not readable",darkText>35);
        assertTrue("Condition chip was not drawn",roseFill>300);
        try(FileOutputStream stream=new FileOutputStream(new File(output,"sick-condition.png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
        }
        bitmap.recycle();
        System.out.println("PASS_NATIVE sick condition below NEW PET with visible high-contrast label");
    }
}
