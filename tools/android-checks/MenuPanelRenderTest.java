package com.example.shortsgesturecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.Shadows;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Production native dialog layouts + native Canvas, with offline synthetic accounts only.
 * Copy alongside PlayDateRenderTest in the isolated Android preview project.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w393dp-h851dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MenuPanelRenderTest {
    final PlayDateRenderTest helper = new PlayDateRenderTest();
    final File output = new File(System.getProperty("mochi.menu.output", "/workspace/artifacts/menu-review-20261013"));
    Activity activity;
    Object game;

    void call(String name) throws Exception {
        Method method=game.getClass().getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(game);
    }
    void setup(boolean signedIn) throws Exception {
        helper.init(); output.mkdirs();
        try { androidx.work.WorkManager.getInstance(RuntimeEnvironment.getApplication()); }
        catch (IllegalStateException absent) {
            androidx.work.WorkManager.initialize(RuntimeEnvironment.getApplication(),new androidx.work.Configuration.Builder().build());
        }
        if (signedIn) {
            // Inject a local Firebase user without any sign-in call or token/network request.
            Class<?> info=Class.forName("com.google.firebase.auth.internal.zzz");
            Object provider=info.getConstructor(String.class,String.class,String.class,String.class,String.class,String.class,boolean.class,String.class)
                .newInstance("preview-user","firebase","Jacques",null,"preview@example.test",null,false,null);
            Object user=Class.forName("com.google.firebase.auth.internal.zzad")
                .getConstructor(FirebaseApp.class,List.class).newInstance(FirebaseApp.getInstance(),Collections.singletonList(provider));
            Class<?> tokenType=Class.forName("com.google.android.gms.internal.firebase-auth-api.zzagl");
            Object token=tokenType.getConstructor(String.class,String.class,Long.class,String.class)
                .newInstance("preview-only-refresh","preview-only-token",Long.valueOf(86400),"Bearer");
            user.getClass().getMethod("zza",tokenType).invoke(user,token);
            Field field=Arrays.stream(FirebaseAuth.class.getDeclaredFields()).filter(f->f.getType()==FirebaseUser.class).findFirst().orElseThrow();
            field.setAccessible(true);field.set(FirebaseAuth.getInstance(),user);
        }
        var controller=Robolectric.buildActivity(Activity.class);
        activity=controller.get();activity.setTheme(R.style.Theme_ZoeyPocketPet);controller.setup();
        game=helper.view(activity,"DRAGON","EGG",null,null);
        activity.setContentView((View)game);
    }
    View find(View view,String text) {
        if (view instanceof TextView && (((TextView)view).getText().toString().equals(text) ||
            ((TextView)view).getText().toString().startsWith(text+"\n"))) return view;
        if (view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View found=find(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    View decor() { return ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(); }
    void click(String text) { View control=find(decor(),text);assertNotNull(text,control);assertTrue(control.performClick()); }
    void save(Bitmap bitmap,String name) throws Exception {
        try(var file=new FileOutputStream(new File(output,name))) {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,file));}
        bitmap.recycle();
    }
    void screenshot(String name) throws Exception {
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
        View decor=dialog.getWindow().getDecorView();
        int w=dialog.getWindow().getAttributes().width,h=dialog.getWindow().getAttributes().height;
        assertTrue(w>0&&h>0);
        decor.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        decor.layout(0,0,w,h);
        Bitmap bitmap=helper.bitmap(game);Canvas canvas=new Canvas(bitmap);helper.full(game,canvas,0);
        Paint dim=new Paint();dim.setColor(Color.argb(110,30,18,50));canvas.drawRect(0,0,bitmap.getWidth(),bitmap.getHeight(),dim);
        canvas.save();canvas.translate((bitmap.getWidth()-w)/2f,(bitmap.getHeight()-h)/2f);decor.draw(canvas);canvas.restore();
        save(bitmap,name);
    }
    void friends() throws Exception {
        PlayDateRenderTest.set(game,"socialFriends",Arrays.asList(
            new SocialPlayManager.Friend("zoe","Zoe","Bunny","BUNNY","BABY",true,0,.8f),
            new SocialPlayManager.Friend("bella","Bella","Sunny","CAT","BABY",false,0,.3f),
            new SocialPlayManager.Friend("adam","Adam","Sparky","DRAGON","YOUNG",true,0,.3f)));
    }
    @Test public void renderMenuSettingsContactsAndInvites() throws Exception {
        setup(true);
        PlayDateRenderTest.set(game,"menuOpen",true);PlayDateRenderTest.set(game,"menuOpening",true);PlayDateRenderTest.set(game,"menuAnimationStart",0L);
        Bitmap bitmap=helper.bitmap(game);Canvas canvas=new Canvas(bitmap);helper.full(game,canvas,0);
        PlayDateRenderTest.draw(game,"drawMenu",canvas,1000);
        // Drawer gradient must be opaque, not inherit the dim overlay's alpha.
        int pixel=bitmap.getPixel(15,550);float fraction=550f/bitmap.getHeight();
        assertEquals(Math.round(235+(255-235)*fraction),Color.red(pixel),2);
        assertEquals(Math.round(225+(248-225)*fraction),Color.green(pixel),2);
        assertEquals(Math.round(253+(252-253)*fraction),Color.blue(pixel),2);
        save(bitmap,"menu.png");
        call("showSettings");
        for(String heading:new String[]{"YOU & YOUR PET","GOOGLE BACKUP","NOTIFICATIONS","HELP & LEARNING","SAVED DATA"}) assertNotNull(heading,find(decor(),heading));
        assertNull(find(decor(),"Invite friend"));assertNotNull(find(decor(),"Reset data…"));
        screenshot("settings.png");click("Done");
        friends();call("showFriendsCenter");assertNotNull(find(decor(),"Zoe"));assertNotNull(find(decor(),"● Offline"));
        screenshot("friends-contacts.png");click("Invite friend");
        PlayDateRenderTest.set(game,"socialInviteCode","MOCHI7");call("renderSocialDialog");
        screenshot("friends-invite.png");
        click("Copy code");assertNotNull(find(decor(),"Code copied. Send it to your friend!"));
        var clipboard=(android.content.ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        assertEquals("MOCHI7",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        click("Share code");
        android.content.Intent chooser=Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(android.content.Intent.ACTION_CHOOSER,chooser.getAction());
        android.content.Intent share=chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT);
        assertTrue(share.getStringExtra(android.content.Intent.EXTRA_TEXT).endsWith("MOCHI7"));
        click("Send friend request");assertNotNull(((EditText)PlayDateRenderTest.get(game,"socialCodeInput")).getError());
        // Incoming presence updates must not erase the code being typed.
        EditText input=(EditText)PlayDateRenderTest.get(game,"socialCodeInput");input.setText("abc123");input.requestFocus();input.setSelection(2);
        call("renderSocialDialog");assertEquals("ABC123",((EditText)PlayDateRenderTest.get(game,"socialCodeInput")).getText().toString());
        assertEquals(2,((EditText)PlayDateRenderTest.get(game,"socialCodeInput")).getSelectionStart());
        click("Contacts");assertNotNull(find(decor(),"Zoe"));
        PlayDateRenderTest.set(game,"socialStatusMessage","");
        PlayDateRenderTest.set(game,"socialFriendRequests",Collections.singletonList(new SocialPlayManager.FriendRequest("request","amelia","preview-user","Amelia","Mochi","pending","fixture/request")));
        call("renderSocialDialog");assertNotNull(find(decor(),"Accept"));assertNotNull(find(decor(),"Decline"));
        screenshot("friends-request.png");click("Done");
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNull(PlayDateRenderTest.get(game,"socialPanel"));
    }
    @Test public void settingsActionsAndMenuNavigation() throws Exception {
        setup(true);call("showSettings");
        boolean enabled=CareReminderScheduler.isEnabled(activity);
        click("Care reminders: "+(enabled?"On":"Off"));
        assertEquals(!enabled,CareReminderScheduler.isEnabled(activity));
        assertNotNull(find(decor(),"Care reminders: "+(!enabled?"On":"Off")));
        click("Reset data…");assertEquals("What should be reset?",Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
        Method rect=game.getClass().getDeclaredMethod("menuFriendsRect");rect.setAccessible(true);
        android.graphics.RectF bounds=(android.graphics.RectF)rect.invoke(game);
        Method touch=game.getClass().getDeclaredMethod("handleMenuTouch",MotionEvent.class);touch.setAccessible(true);
        MotionEvent event=MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
        touch.invoke(game,event);event.recycle();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(240));
        assertNotNull(find(decor(),"Contacts"));assertNotNull(find(decor(),"Invite friend"));
    }
    @Test @Config(qualifiers="w320dp-h640dp-hdpi")
    public void smallPhoneLargeTextAndSignedOut() throws Exception {
        RuntimeEnvironment.setFontScale(1.3f);
        setup(false);call("showSettings");assertNotNull(find(decor(),"Sign in with Google"));
        screenshot("settings-small-phone.png");click("Done");call("showFriendsCenter");
        assertNotNull(find(decor(),"Sign in with Google"));screenshot("friends-sign-in.png");click("Done");
    }
}
